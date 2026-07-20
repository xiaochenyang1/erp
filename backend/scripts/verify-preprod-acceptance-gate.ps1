param(
    [string]$EvidenceDirectory,
    [string]$EvidenceIndexPath,
    [string]$BaseUrl,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId,
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$powerShellExe = (Get-Process -Id $PID).Path
if ([string]::IsNullOrWhiteSpace($powerShellExe)) {
    $powerShellExe = "powershell.exe"
}

$gateSteps = [System.Collections.Generic.List[object]]::new()
$gateSections = [System.Collections.Generic.List[string]]::new()
$readinessRegistrationFailure = $null
$gateEvidenceIndex = $null
$gateReadinessHeaders = $null

function Add-GateReportSection {
    param(
        [string]$Title,
        [string]$Body
    )

    $gateSections.Add("")
    $gateSections.Add("## $Title")
    $gateSections.Add("")
    $gateSections.Add($Body.TrimEnd())
}

function Format-GateCommandPart {
    param([string]$Value)

    if ([string]::IsNullOrEmpty($Value)) {
        return '""'
    }
    if ($Value -match "\s" -or $Value.Contains('"')) {
        return '"' + $Value.Replace('"', '`"') + '"'
    }
    return $Value
}

function Format-GateCommandLine {
    param(
        [string]$ScriptFile,
        [string[]]$Arguments
    )

    $safeArguments = [System.Collections.Generic.List[string]]::new()
    $redactNext = $false
    foreach ($argument in $Arguments) {
        if ($redactNext) {
            $safeArguments.Add("<redacted>")
            $redactNext = $false
            continue
        }

        $safeArguments.Add($argument)
        if ($argument -eq "-Password" -or $argument -eq "-AccessToken") {
            $redactNext = $true
        }
    }

    $parts = [System.Collections.Generic.List[string]]::new()
    $parts.Add(".\scripts\$ScriptFile")
    foreach ($argument in $safeArguments) {
        $parts.Add((Format-GateCommandPart $argument))
    }
    return $parts -join " "
}

function Add-GateArgumentIfValue {
    param(
        [System.Collections.Generic.List[string]]$Arguments,
        [string]$Name,
        [object]$Value
    )

    if ($null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value)) {
        $Arguments.Add($Name)
        $Arguments.Add([string]$Value)
    }
}

function Add-GateSwitchIfPresent {
    param(
        [System.Collections.Generic.List[string]]$Arguments,
        [string]$Name,
        [bool]$Enabled
    )

    if ($Enabled) {
        $Arguments.Add($Name)
    }
}

function Get-GateEvidenceDirectory {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        return (Resolve-Path -LiteralPath $EvidenceDirectory).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceIndexPath)) {
        if (-not (Test-Path -LiteralPath $EvidenceIndexPath -PathType Leaf)) {
            throw "EvidenceIndexPath does not exist: $EvidenceIndexPath"
        }
        return (Split-Path -Path (Resolve-Path -LiteralPath $EvidenceIndexPath).Path -Parent)
    }

    throw "Provide -EvidenceDirectory or -EvidenceIndexPath."
}

function Get-GateFallbackManifestPaths {
    param([string]$Directory)

    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return @()
    }
    return @(Get-ChildItem -Path (Join-Path $Directory "*-readiness-evidence-pending-upload.json") -File -ErrorAction SilentlyContinue)
}

function Get-GateEvidenceIndex {
    param([string]$Path)

    if ($null -ne $script:gateEvidenceIndex) {
        return $script:gateEvidenceIndex
    }
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    try {
        $script:gateEvidenceIndex = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        return $script:gateEvidenceIndex
    }
    catch {
        Add-GateReportSection "Evidence index read warning" "Gate readiness registration could not parse evidence index `$Path`: $(($_ | Out-String).Trim())"
        return $null
    }
}

function Get-GateEffectiveReadinessRunId {
    param([string]$IndexPath)

    if ($ReadinessRunId -gt 0) {
        return $ReadinessRunId
    }

    $index = Get-GateEvidenceIndex -Path $IndexPath
    if ($null -eq $index -or $null -eq $index.ReadinessRunId -or [string]::IsNullOrWhiteSpace([string]$index.ReadinessRunId)) {
        return 0
    }

    return [long]$index.ReadinessRunId
}

function Get-GateEffectiveBaseUrl {
    param([string]$IndexPath)

    $effectiveBaseUrl = $BaseUrl
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        $index = Get-GateEvidenceIndex -Path $IndexPath
        if ($null -ne $index) {
            $effectiveBaseUrl = [string]$index.baseUrl
        }
    }
    if ([string]::IsNullOrWhiteSpace($effectiveBaseUrl)) {
        throw "Provide -BaseUrl or an evidence-index.json with baseUrl for readiness approval gate registration."
    }

    return $effectiveBaseUrl.TrimEnd("/")
}

function Get-GateReadinessHeaders {
    param([string]$EffectiveBaseUrl)

    if ($null -ne $script:gateReadinessHeaders) {
        return $script:gateReadinessHeaders
    }

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $script:gateReadinessHeaders = @{
            Authorization = "Bearer $AccessToken"
        }
        return $script:gateReadinessHeaders
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password for readiness approval gate registration."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[preprod-gate] POST /api/auth/login for approval gate readiness registration")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$EffectiveBaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    $script:gateReadinessHeaders = @{
        Authorization = "Bearer $token"
    }
    return $script:gateReadinessHeaders
}

function Invoke-GateStep {
    param(
        [string]$Name,
        [string]$ScriptFile,
        [string[]]$Arguments
    )

    $scriptPath = Join-Path $PSScriptRoot $ScriptFile
    $commandText = Format-GateCommandLine -ScriptFile $ScriptFile -Arguments $Arguments
    $startedAt = Get-Date
    $consoleOutput = ""
    $exitCode = 0
    $status = "PASSED"
    $failureReason = $null

    [System.Console]::WriteLine("[preprod-gate] $Name")
    try {
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
            throw "Gate script not found: $scriptPath"
        }

        $processArguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $scriptPath) + $Arguments
        $consoleOutput = (& $powerShellExe @processArguments 2>&1 | Out-String).TrimEnd()
        if ($LASTEXITCODE -is [int]) {
            $exitCode = $LASTEXITCODE
        }
        if ($exitCode -ne 0) {
            throw "$ScriptFile failed with exit code $exitCode"
        }
    }
    catch {
        if ($exitCode -eq 0) {
            $exitCode = 1
        }
        $status = "FAILED"
        $failureReason = ($_ | Out-String).Trim()
    }

    $result = [pscustomobject]@{
        Name = $Name
        ScriptFile = $ScriptFile
        Status = $status
        ExitCode = $exitCode
        Command = $commandText
        ConsoleOutput = $consoleOutput
        FailureReason = $failureReason
        StartedAt = $startedAt
        FinishedAt = Get-Date
    }
    $gateSteps.Add($result)

    $failureBlock = ""
    if (-not [string]::IsNullOrWhiteSpace($failureReason)) {
        $failureBlock = @"

Failure:

````
$failureReason
````
"@
    }

    Add-GateReportSection $Name @"
Command:

````powershell
$commandText
````

Status: $status

Exit code: $exitCode

Console output:

````
$consoleOutput
````
$failureBlock
"@

    return $result
}

function Add-GateSyntheticStep {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    $result = [pscustomobject]@{
        Name = $Name
        ScriptFile = "verify-preprod-acceptance-gate.ps1"
        Status = $Status
        ExitCode = 0
        Command = "n/a"
        ConsoleOutput = $Detail
        FailureReason = $null
        StartedAt = Get-Date
        FinishedAt = Get-Date
    }
    $gateSteps.Add($result)
    Add-GateReportSection $Name @"
Status: $Status

Detail:

````
$Detail
````
"@
}

function Get-GateJsonReportPath {
    param([string]$EffectiveOutputPath)

    if ([System.IO.Path]::GetFileName($EffectiveOutputPath) -ieq "preprod-acceptance-gate.md") {
        $outputDirectory = Split-Path -Path $EffectiveOutputPath -Parent
        if ([string]::IsNullOrWhiteSpace($outputDirectory)) {
            return "preprod-acceptance-gate.json"
        }
        return (Join-Path $outputDirectory "preprod-acceptance-gate.json")
    }

    return [System.IO.Path]::ChangeExtension($EffectiveOutputPath, ".json")
}

function Save-GateReport {
    param(
        [string]$EffectiveEvidenceDirectory,
        [string]$EffectiveOutputPath,
        [string]$Verdict
    )

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Step | Status | Exit code | Script |")
    $rows.Add("|---|---|---|---|")
    foreach ($step in $gateSteps) {
        $exitCode = "n/a"
        if ($null -ne $step.ExitCode) {
            $exitCode = "$($step.ExitCode)"
        }
        $rows.Add("| $($step.Name) | $($step.Status) | $exitCode | ``$($step.ScriptFile)`` |")
    }

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Preproduction acceptance approval gate")
    $lines.Add("")
    $lines.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $lines.Add("- Repository: $RepoRoot")
    $lines.Add("- Evidence directory: $EffectiveEvidenceDirectory")
    $lines.Add("- Verdict: $Verdict")
    $lines.Add("")
    $lines.Add("## Gate summary")
    $lines.Add("")
    $lines.Add($rows -join [Environment]::NewLine)
    foreach ($section in $gateSections) {
        $lines.Add($section)
    }

    $outputDirectory = Split-Path -Path $EffectiveOutputPath -Parent
    if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    $lines -join [Environment]::NewLine | Set-Content -LiteralPath $EffectiveOutputPath -Encoding UTF8

    $jsonOutputPath = Get-GateJsonReportPath -EffectiveOutputPath $EffectiveOutputPath
    Save-GateJsonReport -EffectiveEvidenceDirectory $EffectiveEvidenceDirectory -EffectiveOutputPath $EffectiveOutputPath -JsonOutputPath $jsonOutputPath -Verdict $Verdict
}

function Get-GateReadinessStatus {
    param([string]$Verdict)

    if ($Verdict -eq "READY_FOR_APPROVAL") {
        return "PASSED"
    }

    $failed = @($gateSteps | Where-Object { $_.Status -eq "FAILED" })
    if ($failed.Count -gt 0) {
        return "FAILED"
    }

    return "BLOCKED"
}

function Get-GateFailureReason {
    param([string]$Verdict)

    if ($Verdict -eq "READY_FOR_APPROVAL") {
        return $null
    }

    $blocking = @($gateSteps | Where-Object { $_.Status -ne "PASSED" })
    if ($blocking.Count -gt 0) {
        return ($blocking | ForEach-Object { "$($_.Name)=$($_.Status)" }) -join ", "
    }

    return "Preproduction acceptance approval gate ended with BLOCKED."
}

function Save-GateJsonReport {
    param(
        [string]$EffectiveEvidenceDirectory,
        [string]$EffectiveOutputPath,
        [string]$JsonOutputPath,
        [string]$Verdict
    )

    $readinessRegistrationFailureText = $null
    if ($null -ne $readinessRegistrationFailure) {
        $readinessRegistrationFailureText = ($readinessRegistrationFailure | Out-String).Trim()
    }

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        repository = $RepoRoot
        evidenceDirectory = $EffectiveEvidenceDirectory
        markdownReportPath = $EffectiveOutputPath
        jsonReportPath = $JsonOutputPath
        verdict = $Verdict
        readinessStatus = (Get-GateReadinessStatus -Verdict $Verdict)
        failureReason = (Get-GateFailureReason -Verdict $Verdict)
        readinessRegistrationFailed = ($null -ne $readinessRegistrationFailure)
        readinessRegistrationFailure = $readinessRegistrationFailureText
        stepCount = @($gateSteps).Count
        failedStepCount = @($gateSteps | Where-Object { $_.Status -ne "PASSED" }).Count
        steps = @($gateSteps | ForEach-Object {
            [ordered]@{
                name = $_.Name
                scriptFile = $_.ScriptFile
                status = $_.Status
                exitCode = $_.ExitCode
                command = $_.Command
                consoleOutput = $_.ConsoleOutput
                failureReason = $_.FailureReason
                startedAt = $_.StartedAt
                finishedAt = $_.FinishedAt
            }
        })
    }

    $jsonDirectory = Split-Path -Path $JsonOutputPath -Parent
    if ($jsonDirectory -and -not (Test-Path -LiteralPath $jsonDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $jsonDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $JsonOutputPath -Encoding UTF8

    [System.Console]::WriteLine("[preprod-gate] Gate JSON report written to $JsonOutputPath")
}

function Register-GateReadinessEvidence {
    param(
        [string]$EffectiveIndexPath,
        [string]$EffectiveOutputPath,
        [string]$Verdict
    )

    $effectiveReadinessRunId = Get-GateEffectiveReadinessRunId -IndexPath $EffectiveIndexPath
    if ($effectiveReadinessRunId -le 0) {
        throw "ReadinessRunId is required for readiness approval gate registration. Provide -ReadinessRunId or use an evidence-index.json with ReadinessRunId."
    }
    if (-not (Test-Path -LiteralPath $EffectiveOutputPath -PathType Leaf)) {
        throw "Gate report does not exist for readiness approval gate registration: $EffectiveOutputPath"
    }

    $effectiveBaseUrl = Get-GateEffectiveBaseUrl -IndexPath $EffectiveIndexPath
    $headers = Get-GateReadinessHeaders -EffectiveBaseUrl $effectiveBaseUrl
    $status = Get-GateReadinessStatus -Verdict $Verdict
    $failureReason = Get-GateFailureReason -Verdict $Verdict
    $report = Get-Content -LiteralPath $EffectiveOutputPath -Raw

    $registration = Register-ReadinessEvidenceWithOfflineFallback `
        -BaseUrl $effectiveBaseUrl `
        -Headers $headers `
        -ReadinessRunId $effectiveReadinessRunId `
        -ItemCode "PREPROD_APPROVAL_GATE" `
        -ItemName "审批前总门禁" `
        -Category "DEPLOYMENT" `
        -Priority "P0" `
        -ExpectedResult "证据索引、离线补传校验和系统 readiness 证据对账全部通过，总门禁报告登记为 READY_FOR_APPROVAL" `
        -Status $status `
        -ActualResult "审批前总门禁：$Verdict；报告：$EffectiveOutputPath" `
        -FailureReason $failureReason `
        -EvidenceSummary "审批前总门禁：$Verdict" `
        -EvidenceDetail $report `
        -EvidenceRequestUri $EffectiveOutputPath `
        -BusinessType "PREPROD_APPROVAL_GATE" `
        -BusinessNo "PREPROD-APPROVAL-GATE"

    Add-GateReportSection "Readiness approval gate evidence registration" @"
Readiness run ID: $effectiveReadinessRunId
Readiness item code: PREPROD_APPROVAL_GATE
Readiness item ID: $($registration.ItemId)
Readiness evidence ID: $($registration.EvidenceId)
Readiness attachment ID: $($registration.AttachmentId)
Readiness item status: $($registration.Status)
"@
}

$effectiveEvidenceDirectory = Get-GateEvidenceDirectory
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $effectiveEvidenceDirectory "preprod-acceptance-gate.md"
}

$effectiveIndexPath = $EvidenceIndexPath
if ([string]::IsNullOrWhiteSpace($effectiveIndexPath)) {
    $effectiveIndexPath = Join-Path $effectiveEvidenceDirectory "evidence-index.json"
}

Push-Location $RepoRoot
try {
    $indexArguments = [System.Collections.Generic.List[string]]::new()
    Add-GateArgumentIfValue $indexArguments "-EvidenceIndexPath" $effectiveIndexPath
    Add-GateArgumentIfValue $indexArguments "-EvidenceDirectory" $effectiveEvidenceDirectory
    Add-GateSwitchIfPresent $indexArguments "-RequireUploadedFallback" $true
    Invoke-GateStep "Evidence index approval check" "verify-preprod-evidence-index.ps1" $indexArguments.ToArray() | Out-Null

    $fallbackManifests = Get-GateFallbackManifestPaths -Directory $effectiveEvidenceDirectory
    if ($fallbackManifests.Count -eq 0) {
        Add-GateSyntheticStep "Offline fallback replay validation" "PASSED" "No fallback manifests were found; replay validation is not required."
        Add-GateSyntheticStep "System readiness evidence upload verification" "PASSED" "No fallback manifests were found; system upload verification is not required."
    }
    else {
        $replayArguments = [System.Collections.Generic.List[string]]::new()
        Add-GateArgumentIfValue $replayArguments "-ManifestDirectory" $effectiveEvidenceDirectory
        Add-GateArgumentIfValue $replayArguments "-BaseUrl" $BaseUrl
        Add-GateSwitchIfPresent $replayArguments "-ValidateOnly" $true
        Invoke-GateStep "Offline fallback replay validation" "replay-readiness-evidence.ps1" $replayArguments.ToArray() | Out-Null

        $uploadArguments = [System.Collections.Generic.List[string]]::new()
        Add-GateArgumentIfValue $uploadArguments "-ManifestDirectory" $effectiveEvidenceDirectory
        Add-GateArgumentIfValue $uploadArguments "-BaseUrl" $BaseUrl
        Add-GateArgumentIfValue $uploadArguments "-Username" $Username
        Add-GateArgumentIfValue $uploadArguments "-Password" $Password
        Add-GateArgumentIfValue $uploadArguments "-AccessToken" $AccessToken
        if ($ReadinessRunId -gt 0) {
            Add-GateArgumentIfValue $uploadArguments "-ReadinessRunId" $ReadinessRunId
        }
        Invoke-GateStep "System readiness evidence upload verification" "verify-readiness-evidence-upload.ps1" $uploadArguments.ToArray() | Out-Null
    }
}
finally {
    $blocking = @($gateSteps | Where-Object { $_.Status -ne "PASSED" })
    $verdict = "READY_FOR_APPROVAL"
    if ($blocking.Count -gt 0) {
        $verdict = "BLOCKED"
    }
    Save-GateReport -EffectiveEvidenceDirectory $effectiveEvidenceDirectory -EffectiveOutputPath $OutputPath -Verdict $verdict
    try {
        Register-GateReadinessEvidence -EffectiveIndexPath $effectiveIndexPath -EffectiveOutputPath $OutputPath -Verdict $verdict
    }
    catch {
        $readinessRegistrationFailure = $_
        $gateSteps.Add([pscustomobject]@{
            Name = "Readiness approval gate evidence registration"
            ScriptFile = "readiness-evidence.ps1"
            Status = "FAILED"
            ExitCode = 1
            Command = "Register-ReadinessEvidenceWithOfflineFallback PREPROD_APPROVAL_GATE"
            ConsoleOutput = ""
            FailureReason = ($readinessRegistrationFailure | Out-String).Trim()
            StartedAt = Get-Date
            FinishedAt = Get-Date
        })
        Add-GateReportSection "Readiness approval gate evidence registration failure" ($readinessRegistrationFailure | Out-String)
        $verdict = "BLOCKED"
    }
    Save-GateReport -EffectiveEvidenceDirectory $effectiveEvidenceDirectory -EffectiveOutputPath $OutputPath -Verdict $verdict
    [System.Console]::WriteLine("[preprod-gate] Gate report written to $OutputPath")
    [System.Console]::WriteLine("[preprod-gate] Verdict: $verdict")
    Pop-Location
}

if (@($gateSteps | Where-Object { $_.Status -ne "PASSED" }).Count -gt 0) {
    throw "Preproduction acceptance approval gate ended with BLOCKED. See $OutputPath"
}

if ($readinessRegistrationFailure) {
    throw $readinessRegistrationFailure
}

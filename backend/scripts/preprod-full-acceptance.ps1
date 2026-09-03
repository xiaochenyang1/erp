param(
    [string]$EnvFile = ".env.prod",
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputPath,
    [string]$EvidenceDirectory,
    [string]$Username,
    [string]$Password,
    [string]$AccessToken,
    [long]$ReadinessRunId,
    [string]$ReadinessReleaseVersion,
    [string]$ReadinessEnvironment = "preprod",
    [string]$ReadinessDatabaseInstance,
    [string]$ReadinessRedisInstance,
    [string]$ReadinessDockerProfile = "core",
    [long]$WarehouseId,
    [long]$MaterialWarehouseId,
    [long]$FinishedWarehouseId,
    [string]$BusinessDate = (Get-Date -Format "yyyy-MM-dd"),
    [switch]$SkipReleaseCheck,
    [switch]$SkipComposeUp,
    [switch]$ContinueAfterFailure,
    [switch]$PreflightOnly,
    [switch]$RollbackAfterSuccess,
    [switch]$SkipRollbackOnFailure,
    [switch]$DisableCreatedMasterData
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "readiness-evidence.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $EvidenceDirectory) {
    $EvidenceDirectory = Join-Path $RepoRoot "target\preprod-full-acceptance-$RunStamp"
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $EvidenceDirectory "summary.md"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$sections = [System.Collections.Generic.List[string]]::new()
$stepResults = [System.Collections.Generic.List[object]]::new()
$pipelineFailure = $null
$readinessRegistrationFailure = $null
$readinessHeaders = $null
$authenticatedPermissions = @()
$goNoGoVerdict = "NO-GO"
$skipRemainingReason = "Skipped because a previous acceptance gate failed."
$powerShellExe = (Get-Process -Id $PID).Path
if ([string]::IsNullOrWhiteSpace($powerShellExe)) {
    $powerShellExe = "powershell.exe"
}

function Add-Section {
    param(
        [string]$Title,
        [string]$Body
    )

    $sections.Add("")
    $sections.Add("## $Title")
    $sections.Add("")
    $sections.Add($Body.TrimEnd())
}

function Add-ArgumentIfValue {
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

function Add-SwitchIfPresent {
    param(
        [System.Collections.Generic.List[string]]$Arguments,
        [string]$Name,
        [bool]$Enabled
    )

    if ($Enabled) {
        $Arguments.Add($Name)
    }
}

function Add-AuthArguments {
    param([System.Collections.Generic.List[string]]$Arguments)

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $Arguments.Add("-AccessToken")
        $Arguments.Add($AccessToken)
        return
    }

    $Arguments.Add("-Username")
    $Arguments.Add($Username)
    $Arguments.Add("-Password")
    $Arguments.Add($Password)
}

function New-FullAcceptanceInvocationArguments {
    $arguments = [System.Collections.Generic.List[string]]::new()
    Add-ArgumentIfValue $arguments "-EnvFile" $EnvFile
    Add-ArgumentIfValue $arguments "-BaseUrl" $BaseUrl
    Add-ArgumentIfValue $arguments "-OutputPath" $OutputPath
    Add-ArgumentIfValue $arguments "-EvidenceDirectory" $EvidenceDirectory
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        Add-ArgumentIfValue $arguments "-AccessToken" $AccessToken
    }
    else {
        Add-ArgumentIfValue $arguments "-Username" $Username
        Add-ArgumentIfValue $arguments "-Password" $Password
    }
    if ($ReadinessRunId -gt 0) {
        Add-ArgumentIfValue $arguments "-ReadinessRunId" $ReadinessRunId
    }
    Add-ArgumentIfValue $arguments "-ReadinessReleaseVersion" $ReadinessReleaseVersion
    Add-ArgumentIfValue $arguments "-ReadinessEnvironment" $ReadinessEnvironment
    Add-ArgumentIfValue $arguments "-ReadinessDatabaseInstance" $ReadinessDatabaseInstance
    Add-ArgumentIfValue $arguments "-ReadinessRedisInstance" $ReadinessRedisInstance
    Add-ArgumentIfValue $arguments "-ReadinessDockerProfile" $ReadinessDockerProfile
    if ($WarehouseId -gt 0) {
        Add-ArgumentIfValue $arguments "-WarehouseId" $WarehouseId
    }
    if ($MaterialWarehouseId -gt 0) {
        Add-ArgumentIfValue $arguments "-MaterialWarehouseId" $MaterialWarehouseId
    }
    if ($FinishedWarehouseId -gt 0) {
        Add-ArgumentIfValue $arguments "-FinishedWarehouseId" $FinishedWarehouseId
    }
    Add-ArgumentIfValue $arguments "-BusinessDate" $BusinessDate
    Add-SwitchIfPresent $arguments "-SkipReleaseCheck" $SkipReleaseCheck
    Add-SwitchIfPresent $arguments "-SkipComposeUp" $SkipComposeUp
    Add-SwitchIfPresent $arguments "-ContinueAfterFailure" $ContinueAfterFailure
    Add-SwitchIfPresent $arguments "-PreflightOnly" $PreflightOnly
    Add-SwitchIfPresent $arguments "-RollbackAfterSuccess" $RollbackAfterSuccess
    Add-SwitchIfPresent $arguments "-SkipRollbackOnFailure" $SkipRollbackOnFailure
    Add-SwitchIfPresent $arguments "-DisableCreatedMasterData" $DisableCreatedMasterData
    return $arguments
}

function Get-RedactedFullAcceptanceCommandLine {
    return Format-CommandLine "preprod-full-acceptance.ps1" (New-FullAcceptanceInvocationArguments)
}

function Add-ParameterSelfCheckSection {
    $authStatus = "PASS"
    $authDetail = "Username/password or access token was provided."
    if ([string]::IsNullOrWhiteSpace($AccessToken) -and ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password))) {
        $authStatus = "FAIL"
        $authDetail = "Missing authentication; provide -AccessToken or both -Username and -Password."
    }

    $warehouseStatus = "PASS"
    $warehouseDetail = "WarehouseId was provided."
    if ($WarehouseId -le 0) {
        $warehouseStatus = "FAIL"
        $warehouseDetail = "Missing WarehouseId; provide an active preproduction warehouse id."
    }

    $dateStatus = "PASS"
    $dateDetail = "BusinessDate value: $BusinessDate"
    try {
        [datetime]::ParseExact($BusinessDate, "yyyy-MM-dd", [System.Globalization.CultureInfo]::InvariantCulture) | Out-Null
    }
    catch {
        $dateStatus = "FAIL"
        $dateDetail = "BusinessDate must use yyyy-MM-dd, for example 2026-05-18."
    }

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Parameter | Status | Detail |")
    $rows.Add("|---|---|---|")
    $rows.Add("| Authentication | $authStatus | $authDetail |")
    $rows.Add("| WarehouseId | $warehouseStatus | $warehouseDetail |")
    $rows.Add("| MaterialWarehouseId | INFO | Value: $MaterialWarehouseId; defaults to WarehouseId when omitted. |")
    $rows.Add("| FinishedWarehouseId | INFO | Value: $FinishedWarehouseId; defaults to WarehouseId when omitted. |")
    $rows.Add("| BusinessDate | $dateStatus | $dateDetail |")
    $rows.Add("| PreflightOnly | INFO | $($PreflightOnly.IsPresent) |")

    Add-Section "Parameter self-check" @"
Sanitized command:

````powershell
$(Get-RedactedFullAcceptanceCommandLine)
````

$($rows -join [Environment]::NewLine)

Fix examples:
- Missing authentication: add `-Username admin -Password "<preprod-password>"` or `-AccessToken "<token>"`.
- Missing WarehouseId: add `-WarehouseId <active-warehouse-id>` and optionally `-MaterialWarehouseId <active-material-warehouse-id>` / `-FinishedWarehouseId <active-finished-warehouse-id>`.
- Invalid BusinessDate: use `-BusinessDate "yyyy-MM-dd"` and confirm the account period is OPEN.
"@
}

function Get-ReadinessHeaders {
    if ($null -ne $script:readinessHeaders) {
        return $script:readinessHeaders
    }

    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $script:readinessHeaders = @{
            Authorization = "Bearer $AccessToken"
        }
        return $script:readinessHeaders
    }

    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw "Provide -AccessToken or both -Username and -Password."
    }

    $loginBody = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json

    [System.Console]::WriteLine("[preprod-full] POST /api/auth/login for readiness evidence registration")
    $loginResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" `
        -Body $loginBody `
        -ContentType "application/json" `
        -TimeoutSec 30

    $token = $loginResponse.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Login response did not contain data.accessToken."
    }

    $script:authenticatedPermissions = @($loginResponse.data.permissions)
    $script:readinessHeaders = @{
        Authorization = "Bearer $token"
    }
    return $script:readinessHeaders
}

function Format-CommandPart {
    param([string]$Value)

    if ([string]::IsNullOrEmpty($Value)) {
        return '""'
    }
    if ($Value -match "\s" -or $Value.Contains('"')) {
        return '"' + $Value.Replace('"', '`"') + '"'
    }
    return $Value
}

function Format-CommandLine {
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
        $parts.Add((Format-CommandPart $argument))
    }
    return $parts -join " "
}

function Invoke-AcceptanceStep {
    param(
        [string]$Name,
        [string]$ScriptFile,
        [string[]]$Arguments,
        [string]$ReportPath
    )

    $scriptPath = Join-Path $PSScriptRoot $ScriptFile
    $commandText = Format-CommandLine $ScriptFile $Arguments
    $startedAt = Get-Date
    $consoleOutput = ""
    $exitCode = 0
    $status = "PASSED"
    $failureReason = $null

    [System.Console]::WriteLine("[preprod-full] $Name")
    try {
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
            throw "Acceptance script not found: $scriptPath"
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

    $finishedAt = Get-Date
    $result = [pscustomobject]@{
        Name = $Name
        ScriptFile = $ScriptFile
        Status = $status
        ExitCode = $exitCode
        OutputPath = $ReportPath
        Command = $commandText
        ConsoleOutput = $consoleOutput
        FailureReason = $failureReason
        StartedAt = $startedAt
        FinishedAt = $finishedAt
    }
    $stepResults.Add($result)

    $failureBlock = ""
    if (-not [string]::IsNullOrWhiteSpace($failureReason)) {
        $failureBlock = @"

Failure:

````
$failureReason
````
"@
    }

    Add-Section "$Name result" @"
Command:

````powershell
$commandText
````

Status: $status

Exit code: $exitCode

Evidence file: $ReportPath

Console output:

````
$consoleOutput
````
$failureBlock
"@

    return $result
}

function Add-SkippedStep {
    param(
        [string]$Name,
        [string]$ScriptFile,
        [string]$ReportPath,
        [string]$Reason
    )

    $result = [pscustomobject]@{
        Name = $Name
        ScriptFile = $ScriptFile
        Status = "SKIPPED"
        ExitCode = $null
        OutputPath = $ReportPath
        Command = ".\scripts\$ScriptFile"
        ConsoleOutput = ""
        FailureReason = $Reason
        StartedAt = $null
        FinishedAt = $null
    }
    $stepResults.Add($result)
    Add-Section "$Name result" @"
Status: SKIPPED

Evidence file: $ReportPath

Reason:

````
$Reason
````
"@
}

function Get-ReadinessRunIdFromReport {
    param([string]$ReportPath)

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "Readiness run ID cannot be parsed because evidence report does not exist: $ReportPath"
    }

    $content = Get-Content -LiteralPath $ReportPath -Raw
    $match = [regex]::Match($content, "Readiness run ID:\s*(\d+)")
    if (-not $match.Success) {
        throw "Readiness run ID was not found in preproduction acceptance evidence: $ReportPath"
    }

    return [long]$match.Groups[1].Value
}

function ConvertTo-TriageCell {
    param(
        [object]$Value,
        [string]$Fallback = "n/a"
    )

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return $Fallback
    }

    return ((([string]$Value -replace "\r?\n", " ") -replace "\|", "/").Trim())
}

function Add-FailureTriageIndex {
    $blocking = @($stepResults | Where-Object { $_.Status -ne "PASSED" })
    if ($blocking.Count -eq 0 -and -not $pipelineFailure) {
        Add-Section "Failure triage index" "No failed or skipped acceptance steps."
        return
    }

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Priority | Step | Status | Evidence | Reason | Recommended rerun command |")
    $rows.Add("|---|---|---|---|---|---|")

    foreach ($result in $blocking) {
        $priority = "P1"
        if ($result.Status -eq "FAILED") {
            $priority = "P0"
        }

        $reason = ConvertTo-TriageCell $result.FailureReason "No failure reason captured."
        $command = ConvertTo-TriageCell $result.Command ".\scripts\$($result.ScriptFile)"
        $evidence = ConvertTo-TriageCell $result.OutputPath
        $rows.Add("| $priority | $(ConvertTo-TriageCell $result.Name) | $($result.Status) | ``$evidence`` | $reason | ``$command`` |")
    }

    if ($pipelineFailure -and $blocking.Count -eq 0) {
        $reason = ConvertTo-TriageCell ($pipelineFailure | Out-String) "Pipeline failed before any acceptance step was recorded."
        $command = ConvertTo-TriageCell (Get-RedactedFullAcceptanceCommandLine)
        $rows.Add("| P0 | Full acceptance pipeline | FAILED | ``$OutputPath`` | $reason | ``$command`` |")
    }

    Add-Section "Failure triage index" @"
Review P0 rows first. P1 rows are usually skipped downstream steps after an earlier gate failed or diagnostic mode stopped before business data writes.

$($rows -join [Environment]::NewLine)
"@
}

function Add-GoNoGoSection {
    $blocking = @($stepResults | Where-Object { $_.Status -ne "PASSED" })
    if ($blocking.Count -eq 0 -and $stepResults.Count -gt 0) {
        $script:goNoGoVerdict = "GO"
    }
    else {
        $script:goNoGoVerdict = "NO-GO"
    }

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Step | Script | Status | Exit code | Evidence |")
    $rows.Add("|---|---|---|---|---|")
    foreach ($result in $stepResults) {
        $exitCode = "n/a"
        if ($null -ne $result.ExitCode) {
            $exitCode = "$($result.ExitCode)"
        }
        $rows.Add("| $($result.Name) | ``$($result.ScriptFile)`` | $($result.Status) | $exitCode | ``$($result.OutputPath)`` |")
    }

    $reason = "All configured acceptance steps passed."
    if ($goNoGoVerdict -eq "NO-GO") {
        $failedNames = ($blocking | ForEach-Object { "$($_.Name)=$($_.Status)" }) -join ", "
        if ([string]::IsNullOrWhiteSpace($failedNames)) {
            $failedNames = "no acceptance step completed"
        }
        $reason = "Blocking acceptance result(s): $failedNames"
    }

    Add-Section "Go / No-Go" @"
Verdict: $goNoGoVerdict

Reason: $reason

Readiness run ID: $ReadinessRunId

Evidence directory: $EvidenceDirectory

$($rows -join [Environment]::NewLine)
"@
}

function Stop-AfterFailedStep {
    param([object]$Result)

    if ($Result.Status -eq "PASSED") {
        return $false
    }

    if (-not $ContinueAfterFailure) {
        $script:pipelineFailure = "$($Result.Name) failed; rerun with -ContinueAfterFailure only when you intentionally want diagnostic evidence after a failed gate."
        return $true
    }

    return $false
}

function Get-FullAcceptanceReadinessStatus {
    if ($goNoGoVerdict -eq "GO") {
        return "PASSED"
    }

    $failed = @($stepResults | Where-Object { $_.Status -eq "FAILED" })
    if ($failed.Count -gt 0) {
        return "FAILED"
    }

    return "BLOCKED"
}

function Get-FullAcceptanceFailureReason {
    if ($goNoGoVerdict -eq "GO") {
        return $null
    }

    if ($pipelineFailure) {
        return ($pipelineFailure | Out-String).Trim()
    }

    $blocking = @($stepResults | Where-Object { $_.Status -ne "PASSED" })
    if ($blocking.Count -gt 0) {
        return ($blocking | ForEach-Object { "$($_.Name)=$($_.Status)" }) -join ", "
    }

    return "Preproduction full acceptance ended with NO-GO."
}

function Register-FullAcceptanceEvidence {
    param([string]$Summary)

    if ($ReadinessRunId -le 0) {
        Add-Section "Readiness full acceptance evidence registration" "Skipped because ReadinessRunId was not available."
        return
    }

    $headers = Get-ReadinessHeaders
    $status = Get-FullAcceptanceReadinessStatus
    $failureReason = Get-FullAcceptanceFailureReason

    $registration = Register-ReadinessEvidenceWithOfflineFallback `
        -BaseUrl $BaseUrl `
        -Headers $headers `
        -ReadinessRunId $ReadinessRunId `
        -ItemCode "PREPROD_FULL_ACCEPTANCE" `
        -ItemName "一键预生产验收总判定" `
        -Category "DEPLOYMENT" `
        -Priority "P0" `
        -ExpectedResult "预生产基础验收、业务只读冒烟、采购到付款、销售到收款和生产制造补偿回滚全部通过，并输出 GO 总判定" `
        -Status $status `
        -ActualResult "一键预生产验收总判定：$goNoGoVerdict；汇总报告：$OutputPath" `
        -FailureReason $failureReason `
        -EvidenceSummary "一键预生产验收总判定：$goNoGoVerdict" `
        -EvidenceDetail $Summary `
        -EvidenceRequestUri $OutputPath `
        -BusinessType "PREPROD_FULL_ACCEPTANCE" `
        -BusinessNo "PREPROD-FULL-ACCEPTANCE"

    Add-Section "Readiness full acceptance evidence registration" @"
Readiness run ID: $ReadinessRunId
Readiness item ID: $($registration.ItemId)
Readiness evidence ID: $($registration.EvidenceId)
Readiness attachment ID: $($registration.AttachmentId)
Readiness item status: $($registration.Status)
"@
}

function ConvertTo-EvidenceIndexPath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    return $Path
}

function Test-EvidenceIndexReportExists {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        return $true
    }

    try {
        $fullPath = [System.IO.Path]::GetFullPath($Path)
        $fullOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
        return [string]::Equals($fullPath, $fullOutputPath, [System.StringComparison]::OrdinalIgnoreCase)
    }
    catch {
        return $false
    }
}

function Get-EvidenceIndexFallbackPackages {
    $directories = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        $directories.Add($EvidenceDirectory)
    }

    $outputDirectory = Split-Path -Path $OutputPath -Parent
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and $directories -notcontains $outputDirectory) {
        $directories.Add($outputDirectory)
    }

    $packages = [System.Collections.Generic.List[object]]::new()
    foreach ($directory in $directories) {
        if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
            continue
        }

        $manifestFiles = Get-ChildItem -Path (Join-Path $directory "*-readiness-evidence-pending-upload.json") -File -ErrorAction SilentlyContinue
        foreach ($manifestFile in $manifestFiles) {
            $uploadStatus = "PENDING"
            $itemCode = $null
            try {
                $manifest = Get-Content -LiteralPath $manifestFile.FullName -Raw | ConvertFrom-Json
                if (-not [string]::IsNullOrWhiteSpace([string]$manifest.uploadStatus)) {
                    $uploadStatus = [string]$manifest.uploadStatus
                }
                $itemCode = [string]$manifest.itemCode
            }
            catch {
                $uploadStatus = "UNREADABLE"
            }

            $packages.Add([pscustomobject]@{
                itemCode = $itemCode
                manifestPath = $manifestFile.FullName
                uploadStatus = $uploadStatus
                replayCommand = ".\scripts\replay-readiness-evidence.ps1 -ManifestPath $(Format-CommandPart $manifestFile.FullName) -ValidateOnly"
            })
        }
    }

    return @($packages)
}

function Get-EvidenceIndexObjectProperty {
    param(
        [object]$Object,
        [string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties |
        Where-Object { $_.Name -ieq $Name } |
        Select-Object -First 1
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Get-EvidenceIndexReleaseCheckMetadata {
    $reportDirectory = Join-Path $RepoRoot "target"
    $reportPath = Join-Path $reportDirectory "release-check-report.json"
    $currentHeadCommit = $null

    Push-Location $RepoRoot
    try {
        $commitOutput = & git rev-parse --short HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $null -ne $commitOutput) {
            $currentHeadCommit = ([string]$commitOutput).Trim()
        }
    }
    finally {
        Pop-Location
    }

    $status = "NOT_FOUND"
    $allowDirtyWorktree = $null
    $releaseCandidateCommit = $currentHeadCommit
    $candidateSource = "git-head"
    $reportExists = Test-Path -LiteralPath $reportPath -PathType Leaf
    $reportReadError = $null

    if ($reportExists) {
        try {
            $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
            $reportedStatus = Get-EvidenceIndexObjectProperty -Object $report -Name "status"
            if (-not [string]::IsNullOrWhiteSpace([string]$reportedStatus)) {
                $status = ([string]$reportedStatus).Trim()
            }
            else {
                $status = "UNKNOWN"
            }

            $reportedCandidate = Get-EvidenceIndexObjectProperty -Object $report -Name "releaseCandidateCommit"
            if (-not [string]::IsNullOrWhiteSpace([string]$reportedCandidate)) {
                $releaseCandidateCommit = ([string]$reportedCandidate).Trim()
                $candidateSource = "release-check-report"
            }
            $allowDirtyWorktree = Get-EvidenceIndexObjectProperty -Object $report -Name "allowDirtyWorktree"
        }
        catch {
            $status = "UNREADABLE"
            $reportReadError = (($_ | Out-String).Trim())
        }
    }

    if ([string]::IsNullOrWhiteSpace([string]$releaseCandidateCommit)) {
        $releaseCandidateCommit = "UNKNOWN"
        $candidateSource = "unresolved"
    }

    return [pscustomobject]@{
        Status = $status
        ReleaseCandidateCommit = $releaseCandidateCommit
        AllowDirtyWorktree = $allowDirtyWorktree
        ReportPath = $reportPath
        ReportExists = $reportExists
        ReportReadError = $reportReadError
        CandidateSource = $candidateSource
        CurrentHeadCommit = $currentHeadCommit
    }
}

function Save-EvidenceIndexManifest {
    $evidenceIndexPath = Join-Path $EvidenceDirectory "evidence-index.json"
    if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
    }

    $reports = [System.Collections.Generic.List[object]]::new()
    $reports.Add([pscustomobject]@{
        name = "Preproduction full acceptance summary"
        type = "SUMMARY"
        path = ConvertTo-EvidenceIndexPath $OutputPath
        exists = (Test-EvidenceIndexReportExists -Path $OutputPath)
    })

    foreach ($result in $stepResults) {
        $reports.Add([pscustomobject]@{
            name = $result.Name
            type = "STEP_REPORT"
            script = $result.ScriptFile
            status = $result.Status
            path = ConvertTo-EvidenceIndexPath $result.OutputPath
            exists = (Test-EvidenceIndexReportExists -Path $result.OutputPath)
        })
    }

    $fallbackPackages = Get-EvidenceIndexFallbackPackages
    $releaseCheck = Get-EvidenceIndexReleaseCheckMetadata
    $index = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        repository = $RepoRoot
        baseUrl = $BaseUrl
        evidenceDirectory = $EvidenceDirectory
        summaryPath = $OutputPath
        ReadinessRunId = $ReadinessRunId
        goNoGoVerdict = $goNoGoVerdict
        releaseCheck = [ordered]@{
            status = $releaseCheck.Status
            releaseCandidateCommit = $releaseCheck.ReleaseCandidateCommit
            allowDirtyWorktree = $releaseCheck.AllowDirtyWorktree
            reportPath = $releaseCheck.ReportPath
            reportExists = $releaseCheck.ReportExists
            reportReadError = $releaseCheck.ReportReadError
            candidateSource = $releaseCheck.CandidateSource
            currentHeadCommit = $releaseCheck.CurrentHeadCommit
        }
        failureTriageSection = "Failure triage index"
        goNoGoSection = "Go / No-Go"
        replayDirectoryCommand = ".\scripts\replay-readiness-evidence.ps1 -ManifestDirectory $(Format-CommandPart $EvidenceDirectory) -ValidateOnly"
        reports = @($reports)
        stepResults = @($stepResults | ForEach-Object {
            [pscustomobject]@{
                name = $_.Name
                script = $_.ScriptFile
                status = $_.Status
                exitCode = $_.ExitCode
                evidence = $_.OutputPath
                command = $_.Command
            }
        })
        fallbackPackages = @($fallbackPackages)
    }

    $index | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $evidenceIndexPath -Encoding UTF8

    return [pscustomobject]@{
        Path = $evidenceIndexPath
        ReportCount = $reports.Count
        FallbackPackageCount = $fallbackPackages.Count
        ReleaseCandidateCommit = $releaseCheck.ReleaseCandidateCommit
        ReleaseCheckReportPath = $releaseCheck.ReportPath
    }
}

function Add-EvidenceIndexSection {
    $index = Save-EvidenceIndexManifest
    Add-Section "Evidence index" @"
Evidence index file: $($index.Path)

Indexed report count: $($index.ReportCount)

Fallback package count: $($index.FallbackPackageCount)

Release candidate commit: $($index.ReleaseCandidateCommit)

Release-check report: $($index.ReleaseCheckReportPath)

Validate fallback packages:

````powershell
.\scripts\replay-readiness-evidence.ps1 -ManifestDirectory $(Format-CommandPart $EvidenceDirectory) -ValidateOnly
````
"@
}

function Invoke-PreflightGet {
    param(
        [string]$Path,
        [hashtable]$Headers
    )

    try {
        return Invoke-RestMethod -Method Get -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
    }
    catch {
        throw "Preflight GET $Path failed: $(($_ | Out-String).Trim())"
    }
}

function Add-PreflightResult {
    param(
        [string]$Status,
        [string]$Reason
    )

    $exitCode = 0
    if ($Status -ne "PASSED") {
        $exitCode = 1
    }

    $stepResults.Add([pscustomobject]@{
        Name = "Preflight validation"
        ScriptFile = "preprod-full-acceptance.ps1"
        Status = $Status
        ExitCode = $exitCode
        OutputPath = $OutputPath
        Command = ".\scripts\preprod-full-acceptance.ps1 preflight"
        ConsoleOutput = ""
        FailureReason = $Reason
        StartedAt = $null
        FinishedAt = $null
    })
}

function Assert-RequiredPermissions {
    param([System.Collections.Generic.List[string]]$Rows)

    $requiredPermissions = @(
        "system:readiness:manage",
        "system:attachment:manage",
        "masterdata:warehouse:view",
        "finance:period:view",
        "masterdata:supplier:create",
        "masterdata:supplier:disable",
        "masterdata:product:create",
        "masterdata:product:disable",
        "masterdata:customer:create",
        "masterdata:customer:disable",
        "purchase:order:create",
        "purchase:order:submit",
        "purchase:order:approve",
        "purchase:receipt:create",
        "purchase:receipt:post",
        "purchase:return:create",
        "purchase:return:post",
        "finance:payment:create",
        "finance:receipt:create",
        "sales:order:create",
        "sales:order:submit",
        "sales:order:approve",
        "sales:delivery:create",
        "sales:delivery:post",
        "sales:return:create",
        "sales:return:post",
        "production:bom:manage",
        "production:order:create",
        "production:order:release",
        "production:order:issue",
        "production:order:complete",
        "production:order:reverse-completion",
        "production:order:return"
    )

    if ($script:authenticatedPermissions.Count -eq 0) {
        $Rows.Add("| Required permissions | SKIPPED | Permission list is unavailable when -AccessToken is used; read endpoint checks still run. |")
        return
    }

    $missing = @($requiredPermissions | Where-Object { $script:authenticatedPermissions -notcontains $_ })
    if ($missing.Count -gt 0) {
        throw "Preflight missing required permissions: $($missing -join ', ')"
    }

    $Rows.Add("| Required permissions | PASS | Required permission count: $($requiredPermissions.Count) |")
}

function Assert-ActiveWarehouse {
    param(
        [string]$Name,
        [long]$Id,
        [hashtable]$Headers,
        [System.Collections.Generic.List[string]]$Rows
    )

    $response = Invoke-PreflightGet -Path "/api/masterdata/warehouses/$Id" -Headers $Headers
    $warehouse = $response.data
    if ($null -eq $warehouse -or [long]$warehouse.id -ne $Id) {
        throw "Preflight warehouse $Name did not return expected id $Id."
    }

    if ("ACTIVE" -ne [string]$warehouse.status) {
        throw "Preflight warehouse $Name id $Id is not ACTIVE. Current status: $($warehouse.status)"
    }

    $Rows.Add("| $Name | PASS | Warehouse `$Id` is ACTIVE. |")
}

function Assert-OpenBusinessPeriod {
    param(
        [hashtable]$Headers,
        [System.Collections.Generic.List[string]]$Rows
    )

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $businessDateValue = [datetime]::ParseExact($BusinessDate, "yyyy-MM-dd", $culture)
    $response = Invoke-PreflightGet -Path "/api/finance/periods?year=$($businessDateValue.Year)" -Headers $Headers
    $period = @($response.data) | Where-Object {
        ([datetime]$_.startDate).Date -le $businessDateValue.Date -and ([datetime]$_.endDate).Date -ge $businessDateValue.Date
    } | Select-Object -First 1

    if ($null -eq $period) {
        throw "Preflight could not find an account period containing BusinessDate $BusinessDate."
    }

    if ("OPEN" -ne [string]$period.status) {
        throw "Preflight account period for BusinessDate $BusinessDate is not OPEN. Current status: $($period.status)"
    }

    $Rows.Add("| BusinessDate | PASS | `$BusinessDate` is inside OPEN account period `$($period.periodMonth)`. |")
}

function Invoke-PreflightValidation {
    [System.Console]::WriteLine("[preprod-full] Preflight validation")
    $headers = Get-ReadinessHeaders
    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("| Check | Result | Detail |")
    $rows.Add("|---|---|---|")

    try {
        Assert-RequiredPermissions -Rows $rows
        Assert-ActiveWarehouse -Name "Acceptance warehouse" -Id $WarehouseId -Headers $headers -Rows $rows
        Assert-ActiveWarehouse -Name "Material warehouse" -Id $MaterialWarehouseId -Headers $headers -Rows $rows
        Assert-ActiveWarehouse -Name "Finished warehouse" -Id $FinishedWarehouseId -Headers $headers -Rows $rows
        Assert-OpenBusinessPeriod -Headers $headers -Rows $rows
        Add-Section "Preflight validation" ($rows -join [Environment]::NewLine)
        Add-PreflightResult -Status "PASSED" -Reason $null
    }
    catch {
        $failure = ($_ | Out-String).Trim()
        $rows.Add("| Failure | FAIL | `$failure` |")
        Add-Section "Preflight validation failure" ($rows -join [Environment]::NewLine)
        Add-PreflightResult -Status "FAILED" -Reason $failure
        throw
    }
}

function Add-PreflightDiagnosticGuidance {
    Add-Section "Diagnostic preflight mode" @"
PreflightOnly: true

No business data was written.

Skipped steps:
- Business smoke
- Purchase to payment
- Sales to cash
- Production manufacturing

Troubleshooting:
- Warehouse failure: confirm WarehouseId, MaterialWarehouseId and FinishedWarehouseId point to ACTIVE preproduction warehouses in the same tenant.
- Account period failure: generate or reopen the account period containing BusinessDate until it is OPEN.
- Permission failure: grant the missing readiness, attachment, warehouse, period and business-chain permissions to the execution account.
- If the permission list is SKIPPED, rerun with -Username and -Password so the login response can be checked.
"@
}

Push-Location $RepoRoot
try {
    New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null

    $sections.Add("# Preproduction full acceptance summary")
    $sections.Add("")
    $sections.Add("- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")")
    $sections.Add("- Repository: $RepoRoot")
    $sections.Add("- Base URL: $BaseUrl")
    $sections.Add("- Environment file: $EnvFile")
    $sections.Add("- Business date: $BusinessDate")
    $sections.Add("- Preflight-only diagnostic mode: $($PreflightOnly.IsPresent)")
    $sections.Add("- Evidence directory: $EvidenceDirectory")
    Add-ParameterSelfCheckSection

    if ([string]::IsNullOrWhiteSpace($AccessToken) -and ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password))) {
        throw "Provide -AccessToken or both -Username and -Password."
    }
    if ($WarehouseId -le 0) {
        throw "WarehouseId is required. Use an active preproduction warehouse."
    }
    if ($MaterialWarehouseId -le 0) {
        $MaterialWarehouseId = $WarehouseId
    }
    if ($FinishedWarehouseId -le 0) {
        $FinishedWarehouseId = $WarehouseId
    }

    $preprodOutputPath = Join-Path $EvidenceDirectory "01-preprod-acceptance.md"
    $businessSmokeOutputPath = Join-Path $EvidenceDirectory "02-business-smoke.md"
    $purchaseToPaymentOutputPath = Join-Path $EvidenceDirectory "03-purchase-to-payment-acceptance.md"
    $salesToCashOutputPath = Join-Path $EvidenceDirectory "04-sales-to-cash-acceptance.md"
    $productionManufacturingOutputPath = Join-Path $EvidenceDirectory "05-production-manufacturing-acceptance.md"

    $preprodArguments = [System.Collections.Generic.List[string]]::new()
    Add-ArgumentIfValue $preprodArguments "-EnvFile" $EnvFile
    Add-ArgumentIfValue $preprodArguments "-BaseUrl" $BaseUrl
    Add-ArgumentIfValue $preprodArguments "-OutputPath" $preprodOutputPath
    Add-AuthArguments $preprodArguments
    if ($ReadinessRunId -gt 0) {
        Add-ArgumentIfValue $preprodArguments "-ReadinessRunId" $ReadinessRunId
    }
    else {
        $preprodArguments.Add("-CreateReadinessRun")
    }
    Add-ArgumentIfValue $preprodArguments "-ReadinessReleaseVersion" $ReadinessReleaseVersion
    Add-ArgumentIfValue $preprodArguments "-ReadinessEnvironment" $ReadinessEnvironment
    Add-ArgumentIfValue $preprodArguments "-ReadinessDatabaseInstance" $ReadinessDatabaseInstance
    Add-ArgumentIfValue $preprodArguments "-ReadinessRedisInstance" $ReadinessRedisInstance
    Add-ArgumentIfValue $preprodArguments "-ReadinessDockerProfile" $ReadinessDockerProfile
    Add-SwitchIfPresent $preprodArguments "-SkipReleaseCheck" $SkipReleaseCheck
    Add-SwitchIfPresent $preprodArguments "-SkipComposeUp" $SkipComposeUp

    $preprodResult = Invoke-AcceptanceStep "Preproduction foundation" "preprod-acceptance.ps1" $preprodArguments.ToArray() $preprodOutputPath
    $runRemaining = -not (Stop-AfterFailedStep $preprodResult)

    if ($runRemaining -and $ReadinessRunId -le 0) {
        try {
            $ReadinessRunId = Get-ReadinessRunIdFromReport $preprodOutputPath
            Add-Section "Readiness run" "Readiness run ID: $ReadinessRunId"
        }
        catch {
            $pipelineFailure = $_
            Add-Section "Readiness run parse failure" ($pipelineFailure | Out-String)
            $runRemaining = $false
        }
    }

    if ($runRemaining) {
        try {
            Invoke-PreflightValidation
        }
        catch {
            $pipelineFailure = $_
            $runRemaining = $false
        }
    }

    if ($runRemaining -and $PreflightOnly) {
        Add-PreflightDiagnosticGuidance
        $skipRemainingReason = "Skipped because -PreflightOnly was specified; diagnostic mode stops before business scripts and writes no business data."
        $runRemaining = $false
    }

    if ($runRemaining) {
        $businessArguments = [System.Collections.Generic.List[string]]::new()
        Add-ArgumentIfValue $businessArguments "-BaseUrl" $BaseUrl
        Add-ArgumentIfValue $businessArguments "-OutputPath" $businessSmokeOutputPath
        Add-AuthArguments $businessArguments
        Add-ArgumentIfValue $businessArguments "-ReadinessRunId" $ReadinessRunId

        $businessResult = Invoke-AcceptanceStep "Business smoke" "business-smoke.ps1" $businessArguments.ToArray() $businessSmokeOutputPath
        $runRemaining = -not (Stop-AfterFailedStep $businessResult)
    }
    else {
        Add-SkippedStep "Business smoke" "business-smoke.ps1" $businessSmokeOutputPath $skipRemainingReason
    }

    if ($runRemaining) {
        $purchaseArguments = [System.Collections.Generic.List[string]]::new()
        Add-ArgumentIfValue $purchaseArguments "-BaseUrl" $BaseUrl
        Add-ArgumentIfValue $purchaseArguments "-OutputPath" $purchaseToPaymentOutputPath
        Add-AuthArguments $purchaseArguments
        Add-ArgumentIfValue $purchaseArguments "-WarehouseId" $WarehouseId
        Add-ArgumentIfValue $purchaseArguments "-BusinessDate" $BusinessDate
        Add-ArgumentIfValue $purchaseArguments "-ReadinessRunId" $ReadinessRunId
        Add-SwitchIfPresent $purchaseArguments "-RollbackAfterSuccess" $RollbackAfterSuccess
        Add-SwitchIfPresent $purchaseArguments "-SkipRollbackOnFailure" $SkipRollbackOnFailure
        Add-SwitchIfPresent $purchaseArguments "-DisableCreatedMasterData" $DisableCreatedMasterData

        $purchaseResult = Invoke-AcceptanceStep "Purchase to payment" "purchase-to-payment-acceptance.ps1" $purchaseArguments.ToArray() $purchaseToPaymentOutputPath
        $runRemaining = -not (Stop-AfterFailedStep $purchaseResult)
    }
    else {
        Add-SkippedStep "Purchase to payment" "purchase-to-payment-acceptance.ps1" $purchaseToPaymentOutputPath $skipRemainingReason
    }

    if ($runRemaining) {
        $salesArguments = [System.Collections.Generic.List[string]]::new()
        Add-ArgumentIfValue $salesArguments "-BaseUrl" $BaseUrl
        Add-ArgumentIfValue $salesArguments "-OutputPath" $salesToCashOutputPath
        Add-AuthArguments $salesArguments
        Add-ArgumentIfValue $salesArguments "-WarehouseId" $WarehouseId
        Add-ArgumentIfValue $salesArguments "-BusinessDate" $BusinessDate
        Add-ArgumentIfValue $salesArguments "-ReadinessRunId" $ReadinessRunId
        Add-SwitchIfPresent $salesArguments "-RollbackAfterSuccess" $RollbackAfterSuccess
        Add-SwitchIfPresent $salesArguments "-SkipRollbackOnFailure" $SkipRollbackOnFailure
        Add-SwitchIfPresent $salesArguments "-DisableCreatedMasterData" $DisableCreatedMasterData

        $salesResult = Invoke-AcceptanceStep "Sales to cash" "sales-to-cash-acceptance.ps1" $salesArguments.ToArray() $salesToCashOutputPath
        $runRemaining = -not (Stop-AfterFailedStep $salesResult)
    }
    else {
        Add-SkippedStep "Sales to cash" "sales-to-cash-acceptance.ps1" $salesToCashOutputPath $skipRemainingReason
    }

    if ($runRemaining) {
        $manufacturingArguments = [System.Collections.Generic.List[string]]::new()
        Add-ArgumentIfValue $manufacturingArguments "-BaseUrl" $BaseUrl
        Add-ArgumentIfValue $manufacturingArguments "-OutputPath" $productionManufacturingOutputPath
        Add-AuthArguments $manufacturingArguments
        Add-ArgumentIfValue $manufacturingArguments "-MaterialWarehouseId" $MaterialWarehouseId
        Add-ArgumentIfValue $manufacturingArguments "-FinishedWarehouseId" $FinishedWarehouseId
        Add-ArgumentIfValue $manufacturingArguments "-BusinessDate" $BusinessDate
        Add-ArgumentIfValue $manufacturingArguments "-ReadinessRunId" $ReadinessRunId
        Add-SwitchIfPresent $manufacturingArguments "-RollbackAfterSuccess" $RollbackAfterSuccess
        Add-SwitchIfPresent $manufacturingArguments "-SkipRollbackOnFailure" $SkipRollbackOnFailure
        Add-SwitchIfPresent $manufacturingArguments "-DisableCreatedMasterData" $DisableCreatedMasterData

        $manufacturingResult = Invoke-AcceptanceStep "Production manufacturing" "production-manufacturing-acceptance.ps1" $manufacturingArguments.ToArray() $productionManufacturingOutputPath
        Stop-AfterFailedStep $manufacturingResult | Out-Null
    }
    else {
        Add-SkippedStep "Production manufacturing" "production-manufacturing-acceptance.ps1" $productionManufacturingOutputPath $skipRemainingReason
    }
}
catch {
    $pipelineFailure = $_
    Add-Section "Failure" ($pipelineFailure | Out-String)
}
finally {
    Add-FailureTriageIndex
    Add-GoNoGoSection
    $summary = $sections -join [Environment]::NewLine
    try {
        Register-FullAcceptanceEvidence -Summary $summary
    }
    catch {
        $readinessRegistrationFailure = $_
        Add-Section "Readiness full acceptance evidence registration failure" ($readinessRegistrationFailure | Out-String)
    }
    Add-EvidenceIndexSection

    $outputDirectory = Split-Path -Path $OutputPath -Parent
    if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    $summary = $sections -join [Environment]::NewLine
    Set-Content -LiteralPath $OutputPath -Value $summary -Encoding UTF8
    [System.Console]::WriteLine("Preproduction full acceptance summary written to $OutputPath")
    [System.Console]::WriteLine("Go / No-Go: $goNoGoVerdict")
    Pop-Location
}

if ($pipelineFailure) {
    throw $pipelineFailure
}

if ($readinessRegistrationFailure) {
    throw $readinessRegistrationFailure
}

if ($goNoGoVerdict -eq "NO-GO" -and -not $PreflightOnly) {
    throw "Preproduction full acceptance ended with NO-GO. See $OutputPath"
}

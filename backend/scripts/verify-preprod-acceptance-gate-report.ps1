param(
    [string]$GateReportPath,
    [string]$EvidenceDirectory,
    [switch]$AllowBlocked
)

$ErrorActionPreference = "Stop"

$gateReportVerificationChecks = [System.Collections.Generic.List[object]]::new()
$gateReportVerificationFailureCount = 0

function Add-GateReportVerificationCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:gateReportVerificationChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[preprod-gate-report-verify] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:gateReportVerificationFailureCount++
    }
}

function Format-GateReportVerificationValue {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return ""
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Get-PreprodAcceptanceGateReportPath {
    if (-not [string]::IsNullOrWhiteSpace($GateReportPath)) {
        if (-not (Test-Path -LiteralPath $GateReportPath -PathType Leaf)) {
            throw "GateReportPath does not exist: $GateReportPath"
        }
        return (Resolve-Path -LiteralPath $GateReportPath).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        $path = Join-Path $EvidenceDirectory "preprod-acceptance-gate.md"
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "EvidenceDirectory does not contain preprod-acceptance-gate.md: $EvidenceDirectory"
        }
        return (Resolve-Path -LiteralPath $path).Path
    }

    throw "Provide -GateReportPath or -EvidenceDirectory."
}

function Get-GateReportJsonPath {
    param([string]$ResolvedGateReportPath)

    $reportDirectory = Split-Path -Path $ResolvedGateReportPath -Parent
    if ([System.IO.Path]::GetFileName($ResolvedGateReportPath) -ieq "preprod-acceptance-gate.md") {
        return (Join-Path $reportDirectory "preprod-acceptance-gate.json")
    }

    return [System.IO.Path]::ChangeExtension($ResolvedGateReportPath, ".json")
}

function Get-GateReportObjectProperty {
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

function Compare-GateReportPath {
    param(
        [string]$ExpectedPath,
        [string]$ActualPath
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedPath) -or [string]::IsNullOrWhiteSpace($ActualPath)) {
        return $false
    }

    try {
        $expectedFullPath = [System.IO.Path]::GetFullPath($ExpectedPath)
        $actualFullPath = [System.IO.Path]::GetFullPath($ActualPath)
        return [string]::Equals($expectedFullPath, $actualFullPath, [System.StringComparison]::OrdinalIgnoreCase)
    }
    catch {
        return [string]::Equals($ExpectedPath, $ActualPath, [System.StringComparison]::OrdinalIgnoreCase)
    }
}

function Assert-GateReportJson {
    param(
        [string]$ResolvedGateReportPath,
        [string]$ResolvedGateJsonPath
    )

    if (-not (Test-Path -LiteralPath $ResolvedGateJsonPath -PathType Leaf)) {
        Add-GateReportVerificationCheck "preprod-acceptance-gate.json" "FAILED" "JSON sidecar does not exist: $ResolvedGateJsonPath"
        return $null
    }

    try {
        $gateReportJson = Get-Content -LiteralPath $ResolvedGateJsonPath -Raw | ConvertFrom-Json
        Add-GateReportVerificationCheck "preprod-acceptance-gate.json" "PASSED" "Parsed JSON sidecar."
    }
    catch {
        Add-GateReportVerificationCheck "preprod-acceptance-gate.json" "FAILED" "JSON sidecar cannot be parsed. $(($_ | Out-String).Trim())"
        return $null
    }

    $schemaVersion = Get-GateReportObjectProperty -Object $gateReportJson -Name "schemaVersion"
    if ([int]$schemaVersion -eq 1) {
        Add-GateReportVerificationCheck "schemaVersion" "PASSED" "schemaVersion is 1."
    }
    else {
        Add-GateReportVerificationCheck "schemaVersion" "FAILED" "schemaVersion is '$schemaVersion', expected 1."
    }

    $markdownReportPath = [string](Get-GateReportObjectProperty -Object $gateReportJson -Name "markdownReportPath")
    if (Compare-GateReportPath -ExpectedPath $ResolvedGateReportPath -ActualPath $markdownReportPath) {
        Add-GateReportVerificationCheck "markdownReportPath" "PASSED" "markdownReportPath matches the Markdown report."
    }
    else {
        Add-GateReportVerificationCheck "markdownReportPath" "FAILED" "markdownReportPath '$markdownReportPath' does not match '$ResolvedGateReportPath'."
    }

    $jsonReportPath = [string](Get-GateReportObjectProperty -Object $gateReportJson -Name "jsonReportPath")
    if (Compare-GateReportPath -ExpectedPath $ResolvedGateJsonPath -ActualPath $jsonReportPath) {
        Add-GateReportVerificationCheck "jsonReportPath" "PASSED" "jsonReportPath matches the JSON sidecar."
    }
    else {
        Add-GateReportVerificationCheck "jsonReportPath" "FAILED" "jsonReportPath '$jsonReportPath' does not match '$ResolvedGateJsonPath'."
    }

    $verdict = ([string](Get-GateReportObjectProperty -Object $gateReportJson -Name "verdict")).ToUpperInvariant()
    if ($verdict -eq "READY_FOR_APPROVAL") {
        Add-GateReportVerificationCheck "verdict" "PASSED" "verdict is READY_FOR_APPROVAL."
    }
    elseif ($verdict -eq "BLOCKED" -and $AllowBlocked) {
        Add-GateReportVerificationCheck "verdict" "PASSED" "verdict is BLOCKED and -AllowBlocked was specified."
    }
    elseif ($verdict -eq "BLOCKED") {
        Add-GateReportVerificationCheck "verdict" "FAILED" "verdict is BLOCKED; use -AllowBlocked only for blocked gate diagnostics."
    }
    else {
        Add-GateReportVerificationCheck "verdict" "FAILED" "verdict is '$verdict', expected READY_FOR_APPROVAL or BLOCKED."
    }

    $readinessStatus = ([string](Get-GateReportObjectProperty -Object $gateReportJson -Name "readinessStatus")).ToUpperInvariant()
    if ($readinessStatus -in @("PASSED", "FAILED", "BLOCKED")) {
        Add-GateReportVerificationCheck "readinessStatus" "PASSED" "readinessStatus is $readinessStatus."
    }
    else {
        Add-GateReportVerificationCheck "readinessStatus" "FAILED" "readinessStatus is '$readinessStatus', expected PASSED, FAILED, or BLOCKED."
    }
    if ($verdict -eq "READY_FOR_APPROVAL" -and $readinessStatus -ne "PASSED") {
        Add-GateReportVerificationCheck "readinessStatus for READY_FOR_APPROVAL" "FAILED" "READY_FOR_APPROVAL must have readinessStatus PASSED."
    }
    elseif ($verdict -eq "READY_FOR_APPROVAL") {
        Add-GateReportVerificationCheck "readinessStatus for READY_FOR_APPROVAL" "PASSED" "readinessStatus is PASSED."
    }

    $steps = @(Get-GateReportObjectProperty -Object $gateReportJson -Name "steps" | Where-Object { $null -ne $_ })
    if ($steps.Count -gt 0) {
        Add-GateReportVerificationCheck "steps" "PASSED" "JSON lists $($steps.Count) gate step(s)."
    }
    else {
        Add-GateReportVerificationCheck "steps" "FAILED" "JSON steps array is empty."
    }

    $stepCount = Get-GateReportObjectProperty -Object $gateReportJson -Name "stepCount"
    if ([int]$stepCount -eq $steps.Count) {
        Add-GateReportVerificationCheck "stepCount" "PASSED" "stepCount matches steps array."
    }
    else {
        Add-GateReportVerificationCheck "stepCount" "FAILED" "stepCount is '$stepCount', expected $($steps.Count)."
    }

    $actualFailedStepCount = @($steps | Where-Object { ([string]$_.status).ToUpperInvariant() -ne "PASSED" }).Count
    $failedStepCount = Get-GateReportObjectProperty -Object $gateReportJson -Name "failedStepCount"
    if ([int]$failedStepCount -eq $actualFailedStepCount) {
        Add-GateReportVerificationCheck "failedStepCount" "PASSED" "failedStepCount matches non-PASSED steps."
    }
    else {
        Add-GateReportVerificationCheck "failedStepCount" "FAILED" "failedStepCount is '$failedStepCount', expected $actualFailedStepCount."
    }

    foreach ($step in $steps) {
        $stepName = [string](Get-GateReportObjectProperty -Object $step -Name "name")
        if ([string]::IsNullOrWhiteSpace($stepName)) {
            Add-GateReportVerificationCheck "step.name" "FAILED" "A gate step is missing name."
        }
        else {
            Add-GateReportVerificationCheck "step.name $stepName" "PASSED" "Step name is present."
        }

        $stepStatus = ([string](Get-GateReportObjectProperty -Object $step -Name "status")).ToUpperInvariant()
        if ($stepStatus -in @("PASSED", "FAILED", "BLOCKED", "SKIPPED")) {
            Add-GateReportVerificationCheck "step.status $stepName" "PASSED" "Step status is $stepStatus."
        }
        else {
            Add-GateReportVerificationCheck "step.status $stepName" "FAILED" "Step status is '$stepStatus'."
        }

        $scriptFile = [string](Get-GateReportObjectProperty -Object $step -Name "scriptFile")
        if ([string]::IsNullOrWhiteSpace($scriptFile)) {
            Add-GateReportVerificationCheck "step.scriptFile $stepName" "FAILED" "Step scriptFile is missing."
        }
        else {
            Add-GateReportVerificationCheck "step.scriptFile $stepName" "PASSED" "Step scriptFile is present."
        }
    }

    return $gateReportJson
}

function Assert-GateReportMarkdown {
    param(
        [string]$ResolvedGateReportPath,
        [object]$GateReportJson
    )

    $markdown = Get-Content -LiteralPath $ResolvedGateReportPath -Raw
    if ($markdown.Contains("# Preproduction acceptance approval gate")) {
        Add-GateReportVerificationCheck "Markdown title" "PASSED" "Markdown title is present."
    }
    else {
        Add-GateReportVerificationCheck "Markdown title" "FAILED" "Markdown title is missing."
    }

    if ($markdown.Contains("## Gate summary")) {
        Add-GateReportVerificationCheck "Markdown gate summary" "PASSED" "Gate summary section is present."
    }
    else {
        Add-GateReportVerificationCheck "Markdown gate summary" "FAILED" "Gate summary section is missing."
    }

    if ($null -eq $GateReportJson) {
        return
    }

    $verdict = [string](Get-GateReportObjectProperty -Object $GateReportJson -Name "verdict")
    if (-not [string]::IsNullOrWhiteSpace($verdict) -and $markdown.Contains("- Verdict: $verdict")) {
        Add-GateReportVerificationCheck "Markdown verdict" "PASSED" "Markdown verdict matches JSON."
    }
    else {
        Add-GateReportVerificationCheck "Markdown verdict" "FAILED" "Markdown does not contain verdict '$verdict'."
    }

    foreach ($step in @(Get-GateReportObjectProperty -Object $GateReportJson -Name "steps" | Where-Object { $null -ne $_ })) {
        $stepName = [string](Get-GateReportObjectProperty -Object $step -Name "name")
        $stepStatus = [string](Get-GateReportObjectProperty -Object $step -Name "status")
        $scriptFile = [string](Get-GateReportObjectProperty -Object $step -Name "scriptFile")
        if (-not [string]::IsNullOrWhiteSpace($stepName) -and $markdown.Contains($stepName)) {
            Add-GateReportVerificationCheck "Markdown step $stepName" "PASSED" "Markdown contains step name."
        }
        else {
            Add-GateReportVerificationCheck "Markdown step $stepName" "FAILED" "Markdown is missing step name."
        }
        if (-not [string]::IsNullOrWhiteSpace($stepStatus) -and $markdown.Contains($stepStatus)) {
            Add-GateReportVerificationCheck "Markdown step status $stepName" "PASSED" "Markdown contains step status."
        }
        else {
            Add-GateReportVerificationCheck "Markdown step status $stepName" "FAILED" "Markdown is missing step status."
        }
        if (-not [string]::IsNullOrWhiteSpace($scriptFile) -and $markdown.Contains($scriptFile)) {
            Add-GateReportVerificationCheck "Markdown step script $stepName" "PASSED" "Markdown contains step script."
        }
        else {
            Add-GateReportVerificationCheck "Markdown step script $stepName" "FAILED" "Markdown is missing step script."
        }
    }
}

function Save-GateReportVerificationReport {
    param(
        [string]$ReportJsonPath,
        [string]$ReportMarkdownPath,
        [string]$ResolvedGateReportPath,
        [string]$ResolvedGateJsonPath,
        [string]$Status,
        [string]$FailureReason
    )

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        gateReportPath = $ResolvedGateReportPath
        gateJsonPath = $ResolvedGateJsonPath
        status = $Status
        allowBlocked = $AllowBlocked.IsPresent
        failureCount = $gateReportVerificationFailureCount
        failureReason = $FailureReason
        checks = @($gateReportVerificationChecks)
    }

    $reportDirectory = Split-Path -Path $ReportJsonPath -Parent
    if ($reportDirectory -and -not (Test-Path -LiteralPath $reportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8

    $markdownLines = [System.Collections.Generic.List[string]]::new()
    $markdownLines.Add("# Preproduction acceptance gate report verification")
    $markdownLines.Add("")
    $markdownLines.Add("| Field | Value |")
    $markdownLines.Add("| --- | --- |")
    $markdownLines.Add("| Status | $(Format-GateReportVerificationValue $Status) |")
    $markdownLines.Add("| Gate report | $(Format-GateReportVerificationValue $ResolvedGateReportPath) |")
    $markdownLines.Add("| Gate JSON | $(Format-GateReportVerificationValue $ResolvedGateJsonPath) |")
    $markdownLines.Add("| Allow blocked | $($AllowBlocked.IsPresent) |")
    $markdownLines.Add("| Failure count | $gateReportVerificationFailureCount |")
    $markdownLines.Add("| Failure reason | $(Format-GateReportVerificationValue $FailureReason) |")
    $markdownLines.Add("")
    $markdownLines.Add("| Check | Status | Detail |")
    $markdownLines.Add("| --- | --- | --- |")
    foreach ($check in @($gateReportVerificationChecks)) {
        $markdownLines.Add("| $(Format-GateReportVerificationValue $check.Name) | $(Format-GateReportVerificationValue $check.Status) | $(Format-GateReportVerificationValue $check.Detail) |")
    }
    $markdownLines | Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8

    [System.Console]::WriteLine("[preprod-gate-report-verify] Verification report JSON: $ReportJsonPath")
    [System.Console]::WriteLine("[preprod-gate-report-verify] Verification report Markdown: $ReportMarkdownPath")
}

$resolvedGateReportPath = Get-PreprodAcceptanceGateReportPath
$resolvedGateJsonPath = Get-GateReportJsonPath -ResolvedGateReportPath $resolvedGateReportPath
$reportDirectory = Split-Path -Path $resolvedGateReportPath -Parent
$verificationReportJsonPath = Join-Path $reportDirectory "preprod-acceptance-gate.verify-report.json"
$verificationReportMarkdownPath = Join-Path $reportDirectory "preprod-acceptance-gate.verify-report.md"
$verificationStatus = "FAILED"
$verificationFailureReason = $null

try {
    $gateReportJson = Assert-GateReportJson -ResolvedGateReportPath $resolvedGateReportPath -ResolvedGateJsonPath $resolvedGateJsonPath
    Assert-GateReportMarkdown -ResolvedGateReportPath $resolvedGateReportPath -GateReportJson $gateReportJson

    if ($gateReportVerificationFailureCount -gt 0) {
        throw "Preproduction acceptance gate report verification failed with $gateReportVerificationFailureCount failed check(s)."
    }

    $verificationStatus = "PASSED"
    [System.Console]::WriteLine("[preprod-gate-report-verify] Preproduction acceptance gate report verification passed: $resolvedGateReportPath")
}
catch {
    $verificationStatus = "FAILED"
    $verificationFailureReason = $_.Exception.Message
    throw
}
finally {
    Save-GateReportVerificationReport -ReportJsonPath $verificationReportJsonPath -ReportMarkdownPath $verificationReportMarkdownPath -ResolvedGateReportPath $resolvedGateReportPath -ResolvedGateJsonPath $resolvedGateJsonPath -Status $verificationStatus -FailureReason $verificationFailureReason
}

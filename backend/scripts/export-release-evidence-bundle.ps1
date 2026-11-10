param(
    [string]$EvidenceDirectory,
    [string]$EvidenceIndexPath,
    [string]$OutputPath,
    [string]$ReleaseCheckReportDirectory,
    [switch]$AllowBlocked
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sha256-helpers.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($ReleaseCheckReportDirectory)) {
    $ReleaseCheckReportDirectory = Join-Path $RepoRoot "target"
}
$bundleChecks = [System.Collections.Generic.List[object]]::new()
$bundleFailureCount = 0

function Add-ReleaseEvidenceBundleCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:bundleChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[release-evidence-bundle] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:bundleFailureCount++
    }
}

function Get-ReleaseEvidenceIndexPath {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceIndexPath)) {
        if (-not (Test-Path -LiteralPath $EvidenceIndexPath -PathType Leaf)) {
            throw "EvidenceIndexPath does not exist: $EvidenceIndexPath"
        }
        return (Resolve-Path -LiteralPath $EvidenceIndexPath).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
            throw "EvidenceDirectory does not exist: $EvidenceDirectory"
        }
        $candidate = Join-Path $EvidenceDirectory "evidence-index.json"
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            throw "EvidenceDirectory does not contain evidence-index.json: $EvidenceDirectory"
        }
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    throw "Provide -EvidenceDirectory or -EvidenceIndexPath."
}

function Get-ReleaseEvidenceDirectory {
    param([string]$IndexPath)

    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        return (Resolve-Path -LiteralPath $EvidenceDirectory).Path
    }

    return (Split-Path -Path $IndexPath -Parent)
}

function Get-ReleaseEvidenceObjectProperty {
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

function Get-ReleaseEvidenceMarkdownTableValue {
    param(
        [string]$Markdown,
        [string]$Field
    )

    foreach ($line in @($Markdown -split "`r?`n")) {
        $trimmedLine = $line.Trim()
        if (-not $trimmedLine.StartsWith("|")) {
            continue
        }

        $columns = @($trimmedLine.Trim("|").Split("|") | ForEach-Object { $_.Trim() })
        if ($columns.Count -lt 2) {
            continue
        }

        if ($columns[0] -eq $Field) {
            return $columns[1]
        }
    }

    return $null
}

function Resolve-ReleaseEvidencePath {
    param(
        [string]$PathValue,
        [string]$EvidenceRoot
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }
    return (Join-Path $EvidenceRoot $PathValue)
}

function Assert-ReleaseEvidenceTextContains {
    param(
        [string]$Name,
        [string]$Path,
        [string]$RequiredText
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck $Name "FAILED" "Required file does not exist: $Path"
        return
    }

    $content = Get-Content -LiteralPath $Path -Raw
    if ($content.Contains($RequiredText)) {
        Add-ReleaseEvidenceBundleCheck $Name "PASSED" "Found required text: $RequiredText"
        return
    }

    Add-ReleaseEvidenceBundleCheck $Name "FAILED" "Required text '$RequiredText' was not found in $Path"
}

function Assert-ReleaseEvidenceJsonPropertyEquals {
    param(
        [string]$Name,
        [string]$Path,
        [string]$PropertyName,
        [string]$ExpectedValue
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck $Name "FAILED" "Required JSON file does not exist: $Path"
        return
    }

    try {
        $json = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleCheck $Name "FAILED" "Cannot parse ${Path}: $(($_ | Out-String).Trim())"
        return
    }

    $actualValue = Get-ReleaseEvidenceObjectProperty -Object $json -Name $PropertyName
    if ($null -eq $actualValue) {
        Add-ReleaseEvidenceBundleCheck $Name "FAILED" "JSON property '$PropertyName' is missing in $Path."
        return
    }

    $actualText = [string]$actualValue
    if ($actualText -eq $ExpectedValue) {
        Add-ReleaseEvidenceBundleCheck $Name "PASSED" "JSON property '$PropertyName' is '$ExpectedValue'."
        return
    }

    Add-ReleaseEvidenceBundleCheck $Name "FAILED" "JSON property '$PropertyName' is '$actualText', expected '$ExpectedValue'."
}

function Assert-ReleaseEvidencePreprodGateVerificationMarkdownStatus {
    param([string]$EvidenceRoot)

    $jsonPath = Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.json"
    if (-not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification JSON report does not exist: $jsonPath"
        return
    }

    $markdownPath = Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.md"
    if (-not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown report does not exist: $markdownPath"
        return
    }

    try {
        $json = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Cannot parse ${jsonPath}: $(($_ | Out-String).Trim())"
        return
    }

    $expectedStatus = [string](Get-ReleaseEvidenceObjectProperty -Object $json -Name "status")
    if ([string]::IsNullOrWhiteSpace($expectedStatus)) {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification JSON status is missing."
        return
    }

    $markdown = Get-Content -LiteralPath $markdownPath -Raw
    $actualStatus = Get-ReleaseEvidenceMarkdownTableValue -Markdown $markdown -Field "Status"
    if ($null -eq $actualStatus -or [string]::IsNullOrWhiteSpace([string]$actualStatus)) {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown Status field is missing."
        return
    }

    $actualText = [string]$actualStatus
    if ($actualText.ToUpperInvariant() -eq $expectedStatus.ToUpperInvariant()) {
        Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "PASSED" "Gate verification Markdown Status matches JSON status '$expectedStatus'."
        return
    }

    Add-ReleaseEvidenceBundleCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown Status is '$actualText', expected '$expectedStatus'."
}

function Assert-ReleaseEvidenceFallbackManifests {
    param([string]$EvidenceRoot)

    $manifests = @(Get-ChildItem -Path (Join-Path $EvidenceRoot "*-readiness-evidence-pending-upload.json") -File -Recurse -ErrorAction SilentlyContinue)
    if ($manifests.Count -eq 0) {
        Add-ReleaseEvidenceBundleCheck "Fallback manifests" "PASSED" "No offline fallback manifests were found."
        return
    }

    foreach ($manifestFile in $manifests) {
        try {
            $manifest = Get-Content -LiteralPath $manifestFile.FullName -Raw | ConvertFrom-Json
        }
        catch {
            Add-ReleaseEvidenceBundleCheck "Fallback manifest JSON" "FAILED" "Cannot parse $($manifestFile.FullName): $(($_ | Out-String).Trim())"
            continue
        }

        $uploadStatus = [string](Get-ReleaseEvidenceObjectProperty -Object $manifest -Name "uploadStatus")
        if ([string]::IsNullOrWhiteSpace($uploadStatus)) {
            $uploadStatus = "PENDING"
        }
        $uploadStatus = $uploadStatus.ToUpperInvariant()

        if ($uploadStatus -eq "UPLOADED") {
            Add-ReleaseEvidenceBundleCheck "Fallback uploadStatus $($manifestFile.Name)" "PASSED" "uploadStatus is UPLOADED."
        }
        else {
            Add-ReleaseEvidenceBundleCheck "Fallback uploadStatus $($manifestFile.Name)" "FAILED" "uploadStatus is $uploadStatus; final release bundle requires UPLOADED unless -AllowBlocked is used."
        }
    }
}

function Assert-ReleaseCheckReportDirtyWorktreePolicy {
    param(
        [object]$ReleaseCheckReport,
        [string]$ReportStatus
    )

    $reportAllowsDirtyWorktree = [bool](Get-ReleaseEvidenceObjectProperty -Object $ReleaseCheckReport -Name "allowDirtyWorktree")
    if ($ReportStatus -eq "PASSED" -and $reportAllowsDirtyWorktree) {
        Add-ReleaseEvidenceBundleCheck "Release check report dirty worktree policy" "FAILED" "release-check PASSED report was generated with -AllowDirtyWorktree; dirty worktree reports are local non-release investigation evidence only."
        return
    }

    Add-ReleaseEvidenceBundleCheck "Release check report dirty worktree policy" "PASSED" "release-check dirty worktree policy accepted."
}

function Assert-ReleaseCheckReports {
    param([string]$ReportDirectory)

    $reportFiles = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    if (-not (Test-Path -LiteralPath $ReportDirectory -PathType Container)) {
        Add-ReleaseEvidenceBundleCheck "Release check report directory" "FAILED" "Release check report directory does not exist: $ReportDirectory"
        return @()
    }

    $jsonPath = Join-Path $ReportDirectory "release-check-report.json"
    $markdownPath = Join-Path $ReportDirectory "release-check-report.md"
    if (-not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "Release check report JSON" "FAILED" "release-check-report.json does not exist in $ReportDirectory"
    }
    else {
        $jsonFile = Get-Item -LiteralPath $jsonPath
        $reportFiles.Add($jsonFile)
        try {
            $releaseCheckReport = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
            Add-ReleaseEvidenceBundleCheck "Release check report JSON" "PASSED" "Parsed release-check-report.json."

            $reportStatus = ([string](Get-ReleaseEvidenceObjectProperty -Object $releaseCheckReport -Name "status")).ToUpperInvariant()
            if ($reportStatus -eq "PASSED") {
                Add-ReleaseEvidenceBundleCheck "Release check report status" "PASSED" "release-check status is PASSED."
            }
            else {
                Add-ReleaseEvidenceBundleCheck "Release check report status" "FAILED" "release-check status is '$reportStatus', expected PASSED."
            }

            Assert-ReleaseCheckReportDirtyWorktreePolicy -ReleaseCheckReport $releaseCheckReport -ReportStatus $reportStatus

            $syntaxGate = Get-ReleaseEvidenceObjectProperty -Object $releaseCheckReport -Name "powerShellScriptSyntaxGate"
            $syntaxGateStatus = ([string](Get-ReleaseEvidenceObjectProperty -Object $syntaxGate -Name "status")).ToUpperInvariant()
            if ($syntaxGateStatus -eq "PASSED") {
                Add-ReleaseEvidenceBundleCheck "Release check PowerShell syntax gate" "PASSED" "PowerShell script syntax gate is PASSED."
            }
            else {
                Add-ReleaseEvidenceBundleCheck "Release check PowerShell syntax gate" "FAILED" "PowerShell script syntax gate is '$syntaxGateStatus', expected PASSED."
            }

            $artifacts = @(Get-ReleaseEvidenceObjectProperty -Object $releaseCheckReport -Name "artifacts")
            if ($artifacts.Count -gt 0) {
                Add-ReleaseEvidenceBundleCheck "Release check artifacts" "PASSED" "release-check report lists $($artifacts.Count) artifact(s)."
            }
            else {
                Add-ReleaseEvidenceBundleCheck "Release check artifacts" "FAILED" "release-check report artifacts is empty."
            }
        }
        catch {
            Add-ReleaseEvidenceBundleCheck "Release check report JSON" "FAILED" "Cannot parse release-check-report.json: $(($_ | Out-String).Trim())"
        }
    }

    if (-not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "Release check report Markdown" "FAILED" "release-check-report.md does not exist in $ReportDirectory"
    }
    else {
        $markdownFile = Get-Item -LiteralPath $markdownPath
        $reportFiles.Add($markdownFile)
        Add-ReleaseEvidenceBundleCheck "Release check report Markdown" "PASSED" "Found release-check-report.md."
    }

    return @($reportFiles)
}

function Read-ReleaseEvidenceIndex {
    param([string]$IndexPath)

    try {
        $index = Get-Content -LiteralPath $IndexPath -Raw | ConvertFrom-Json
        Add-ReleaseEvidenceBundleCheck "evidence-index.json" "PASSED" "Parsed evidence index: $IndexPath"
        return $index
    }
    catch {
        Add-ReleaseEvidenceBundleCheck "evidence-index.json" "FAILED" "Cannot parse evidence index $($IndexPath): $(($_ | Out-String).Trim())"
        return $null
    }
}

function Assert-ReleaseEvidenceCandidateCommit {
    param(
        [object]$Index,
        [string]$ReportDirectory
    )

    $releaseCheck = Get-ReleaseEvidenceObjectProperty -Object $Index -Name "releaseCheck"
    if ($null -eq $releaseCheck) {
        # Evidence indexes created before candidate binding remain exportable;
        # the release-check report is still validated below.
        Add-ReleaseEvidenceBundleCheck "evidence-index releaseCheck" "PASSED" "Legacy evidence index has no releaseCheck binding; candidate consistency check was skipped."
        return
    }

    $indexCandidate = Get-ReleaseEvidenceObjectProperty -Object $releaseCheck -Name "releaseCandidateCommit"
    if ([string]::IsNullOrWhiteSpace([string]$indexCandidate)) {
        Add-ReleaseEvidenceBundleCheck "evidence-index releaseCandidateCommit" "FAILED" "releaseCheck.releaseCandidateCommit is missing or blank."
        return
    }
    $indexCandidate = ([string]$indexCandidate).Trim()

    $reportPath = Join-Path $ReportDirectory "release-check-report.json"
    if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "evidence-index releaseCandidateCommit" "FAILED" "Cannot compare candidate: release-check-report.json does not exist in $ReportDirectory"
        return
    }

    try {
        $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleCheck "evidence-index releaseCandidateCommit" "FAILED" "Cannot parse release-check-report.json for candidate binding: $(($_ | Out-String).Trim())"
        return
    }

    $reportCandidate = Get-ReleaseEvidenceObjectProperty -Object $report -Name "releaseCandidateCommit"
    if ([string]::IsNullOrWhiteSpace([string]$reportCandidate)) {
        Add-ReleaseEvidenceBundleCheck "release-check releaseCandidateCommit" "FAILED" "release-check report releaseCandidateCommit is missing or blank."
        return
    }
    $reportCandidate = ([string]$reportCandidate).Trim()

    if ($indexCandidate.Equals($reportCandidate, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-ReleaseEvidenceBundleCheck "releaseCandidateCommit consistency" "PASSED" "Evidence index candidate matches release-check report candidate $reportCandidate."
    }
    else {
        Add-ReleaseEvidenceBundleCheck "releaseCandidateCommit consistency" "FAILED" "Evidence index candidate $indexCandidate does not match release-check report candidate $reportCandidate."
    }
}

function Assert-ReleaseEvidenceBundlePrerequisites {
    param(
        [object]$Index,
        [string]$IndexPath,
        [string]$EvidenceRoot
    )

    if ($null -eq $Index) {
        Add-ReleaseEvidenceBundleCheck "ReadinessRunId" "FAILED" "ReadinessRunId cannot be checked because evidence-index.json was not parsed."
        Add-ReleaseEvidenceBundleCheck "goNoGoVerdict" "FAILED" "goNoGoVerdict cannot be checked because evidence-index.json was not parsed."
        Add-ReleaseEvidenceBundleCheck "Summary report" "FAILED" "summaryPath cannot be checked because evidence-index.json was not parsed."
        Assert-ReleaseEvidenceTextContains -Name "Preproduction approval gate report" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.md") -RequiredText "READY_FOR_APPROVAL"
        Assert-ReleaseEvidenceJsonPropertyEquals -Name "Preproduction approval gate JSON verdict" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.json") -PropertyName "verdict" -ExpectedValue "READY_FOR_APPROVAL"
        Assert-ReleaseEvidenceJsonPropertyEquals -Name "Preproduction approval gate report verification JSON status" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.json") -PropertyName "status" -ExpectedValue "PASSED"
        Assert-ReleaseEvidenceTextContains -Name "Preproduction approval gate report verification Markdown" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.md") -RequiredText "Preproduction acceptance gate report verification"
        Assert-ReleaseEvidencePreprodGateVerificationMarkdownStatus -EvidenceRoot $EvidenceRoot
        Assert-ReleaseEvidenceTextContains -Name "Readiness release decision report" -Path (Join-Path $EvidenceRoot "readiness-release-decision.md") -RequiredText "DECIDED_GO"
        Assert-ReleaseEvidenceFallbackManifests -EvidenceRoot $EvidenceRoot
        return
    }

    $readinessRunId = Get-ReleaseEvidenceObjectProperty -Object $Index -Name "ReadinessRunId"
    $parsedReadinessRunId = [long]0
    if ($null -ne $readinessRunId -and [long]::TryParse([string]$readinessRunId, [ref]$parsedReadinessRunId) -and $parsedReadinessRunId -gt 0) {
        Add-ReleaseEvidenceBundleCheck "ReadinessRunId" "PASSED" "Readiness run id: $parsedReadinessRunId"
    }
    else {
        $readinessRunIdText = if ($null -eq $readinessRunId) { "<missing>" } else { [string]$readinessRunId }
        Add-ReleaseEvidenceBundleCheck "ReadinessRunId" "FAILED" "ReadinessRunId is missing, non-numeric, or not positive: $readinessRunIdText"
    }

    $goNoGoVerdict = ([string](Get-ReleaseEvidenceObjectProperty -Object $Index -Name "goNoGoVerdict")).ToUpperInvariant()
    if ($goNoGoVerdict -eq "GO") {
        Add-ReleaseEvidenceBundleCheck "goNoGoVerdict" "PASSED" "goNoGoVerdict is GO."
    }
    else {
        Add-ReleaseEvidenceBundleCheck "goNoGoVerdict" "FAILED" "goNoGoVerdict is '$goNoGoVerdict', expected GO."
    }

    $summaryPath = Resolve-ReleaseEvidencePath -PathValue ([string](Get-ReleaseEvidenceObjectProperty -Object $Index -Name "summaryPath")) -EvidenceRoot $EvidenceRoot
    if (-not [string]::IsNullOrWhiteSpace($summaryPath) -and (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleCheck "Summary report" "PASSED" "Summary report exists: $summaryPath"
    }
    else {
        Add-ReleaseEvidenceBundleCheck "Summary report" "FAILED" "summaryPath is missing or the summary file does not exist."
    }

    Assert-ReleaseEvidenceTextContains -Name "Preproduction approval gate report" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.md") -RequiredText "READY_FOR_APPROVAL"
    Assert-ReleaseEvidenceJsonPropertyEquals -Name "Preproduction approval gate JSON verdict" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.json") -PropertyName "verdict" -ExpectedValue "READY_FOR_APPROVAL"
    Assert-ReleaseEvidenceJsonPropertyEquals -Name "Preproduction approval gate report verification JSON status" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.json") -PropertyName "status" -ExpectedValue "PASSED"
    Assert-ReleaseEvidenceTextContains -Name "Preproduction approval gate report verification Markdown" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.md") -RequiredText "Preproduction acceptance gate report verification"
    Assert-ReleaseEvidencePreprodGateVerificationMarkdownStatus -EvidenceRoot $EvidenceRoot
    Assert-ReleaseEvidenceTextContains -Name "Readiness release decision report" -Path (Join-Path $EvidenceRoot "readiness-release-decision.md") -RequiredText "DECIDED_GO"
    Assert-ReleaseEvidenceFallbackManifests -EvidenceRoot $EvidenceRoot
}

function Get-ReleaseEvidenceRelativePath {
    param(
        [string]$Root,
        [string]$Path
    )

    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    $pathFull = [System.IO.Path]::GetFullPath($Path)
    if ($pathFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $pathFull.Substring($rootFull.Length)
    }

    return [System.IO.Path]::GetFileName($pathFull)
}

function New-ReleaseEvidenceSourceFile {
    param(
        [string]$RelativePath,
        [System.IO.FileInfo]$File
    )

    $hash = [pscustomobject]@{ Hash = (Get-Sha256Hex -LiteralPath $File.FullName) }
    return [pscustomobject]@{
        relativePath = $RelativePath
        length = $File.Length
        lastWriteTimeUtc = $File.LastWriteTimeUtc.ToString("o")
        sha256 = $hash.Hash
    }
}

function Save-ReleaseEvidenceBundleManifest {
    param(
        [string]$ManifestPath,
        [string]$EvidenceRoot,
        [string]$IndexPath,
        [string]$BundleStatus,
        [object[]]$SourceFiles
    )

    $manifest = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        repository = $RepoRoot
        evidenceDirectory = $EvidenceRoot
        evidenceIndexPath = $IndexPath
        bundleStatus = $BundleStatus
        allowBlocked = $AllowBlocked.IsPresent
        checks = @($bundleChecks)
        sourceFiles = @($SourceFiles)
    }

    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ManifestPath -Encoding UTF8
}

function Format-ReleaseEvidenceSummaryValue {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return "UNKNOWN"
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Get-ReleaseCheckReportSummary {
    param([string]$ReportDirectory)

    $jsonPath = Join-Path $ReportDirectory "release-check-report.json"
    $summary = [ordered]@{
        Status = "UNKNOWN"
        ReleaseCandidateCommit = "UNKNOWN"
        AllowDirtyWorktree = "UNKNOWN"
    }
    if (-not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        return [pscustomobject]$summary
    }

    try {
        $report = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
        $status = Get-ReleaseEvidenceObjectProperty -Object $report -Name "status"
        $commit = Get-ReleaseEvidenceObjectProperty -Object $report -Name "releaseCandidateCommit"
        $allowDirtyWorktree = Get-ReleaseEvidenceObjectProperty -Object $report -Name "allowDirtyWorktree"
        if (-not [string]::IsNullOrWhiteSpace([string]$status)) {
            $summary.Status = [string]$status
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$commit)) {
            $summary.ReleaseCandidateCommit = [string]$commit
        }
        if ($null -ne $allowDirtyWorktree) {
            $summary.AllowDirtyWorktree = [string]$allowDirtyWorktree
        }
    }
    catch {
        $summary.Status = "UNREADABLE"
    }

    return [pscustomobject]$summary
}

function Save-ReleaseEvidenceBundleSummary {
    param(
        [string]$SummaryPath,
        [string]$SummaryJsonPath,
        [string]$BundlePath,
        [string]$Sha256Path,
        [string]$BundleStatus,
        [string]$BundleSha256,
        [int]$SourceFileCount,
        [string]$ReleaseCheckReportDirectory
    )

    $releaseCheck = Get-ReleaseCheckReportSummary -ReportDirectory $ReleaseCheckReportDirectory
    $failedCheckCount = @($bundleChecks | Where-Object { $_.Status -eq "FAILED" }).Count
    $generatedAt = Get-Date -Format "o"
    $summaryJson = [ordered]@{
        schemaVersion = 1
        generatedAt = $generatedAt
        summaryMarkdownPath = $SummaryPath
        bundlePath = $BundlePath
        sha256Path = $Sha256Path
        bundleStatus = $BundleStatus
        bundleSha256 = $BundleSha256
        sourceFileCount = $SourceFileCount
        failedPrerequisiteCheckCount = $failedCheckCount
        allowBlocked = $AllowBlocked.IsPresent
        releaseCheck = [ordered]@{
            status = $releaseCheck.Status
            releaseCandidateCommit = $releaseCheck.ReleaseCandidateCommit
            allowDirtyWorktree = $releaseCheck.AllowDirtyWorktree
        }
    }
    $summaryMarkdown = @"
# Release evidence bundle summary

| Field | Value |
| --- | --- |
| Bundle status | $(Format-ReleaseEvidenceSummaryValue $BundleStatus) |
| Bundle path | $(Format-ReleaseEvidenceSummaryValue $BundlePath) |
| Bundle SHA-256 | $(Format-ReleaseEvidenceSummaryValue $BundleSha256) |
| SHA-256 file | $(Format-ReleaseEvidenceSummaryValue $Sha256Path) |
| Source files | $SourceFileCount |
| Failed prerequisite checks | $failedCheckCount |
| Release check status | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.Status) |
| Release candidate commit | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.ReleaseCandidateCommit) |
| Release check allow dirty worktree | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.AllowDirtyWorktree) |
| Generated at | $generatedAt |
"@

    $summaryDirectory = Split-Path -Path $SummaryPath -Parent
    if ($summaryDirectory -and -not (Test-Path -LiteralPath $summaryDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $summaryDirectory -Force | Out-Null
    }
    $summaryMarkdown | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

    $summaryJsonDirectory = Split-Path -Path $SummaryJsonPath -Parent
    if ($summaryJsonDirectory -and -not (Test-Path -LiteralPath $summaryJsonDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $summaryJsonDirectory -Force | Out-Null
    }
    $summaryJson | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $SummaryJsonPath -Encoding UTF8

    $githubStepSummary = $env:GITHUB_STEP_SUMMARY
    if (-not [string]::IsNullOrWhiteSpace($githubStepSummary)) {
        Add-Content -LiteralPath $githubStepSummary -Value $summaryMarkdown -Encoding UTF8
    }
}

function New-ReleaseEvidenceArtifactIndexEntry {
    param(
        [string]$Role,
        [string]$Path,
        [string]$Status
    )

    $entry = [ordered]@{
        role = $Role
        path = $Path
        fileName = if ([string]::IsNullOrWhiteSpace($Path)) { "UNKNOWN" } else { Split-Path -Path $Path -Leaf }
        status = $Status
        exists = $false
        length = 0
        sha256 = "UNKNOWN"
        lastWriteTimeUtc = "UNKNOWN"
    }

    if (-not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path -LiteralPath $Path -PathType Leaf)) {
        $file = Get-Item -LiteralPath $Path
        $hash = [pscustomobject]@{ Hash = (Get-Sha256Hex -LiteralPath $Path) }
        $entry.status = $Status
        $entry.exists = $true
        $entry.length = $file.Length
        $entry.sha256 = $hash.Hash
        $entry.lastWriteTimeUtc = $file.LastWriteTimeUtc.ToString("o")
    }
    else {
        $entry.status = "MISSING"
    }

    return [pscustomobject]$entry
}

function Get-ReleaseEvidenceVerificationReportStatus {
    param([string]$VerifyReportJsonPath)

    if ([string]::IsNullOrWhiteSpace($VerifyReportJsonPath) -or -not (Test-Path -LiteralPath $VerifyReportJsonPath -PathType Leaf)) {
        return "MISSING"
    }

    try {
        $report = Get-Content -LiteralPath $VerifyReportJsonPath -Raw | ConvertFrom-Json
        $status = Get-ReleaseEvidenceObjectProperty -Object $report -Name "status"
        if ([string]::IsNullOrWhiteSpace([string]$status)) {
            return "UNKNOWN"
        }
        return [string]$status
    }
    catch {
        return "UNREADABLE"
    }
}

function Save-ReleaseEvidenceArtifactsIndex {
    param(
        [string]$IndexJsonPath,
        [string]$IndexMarkdownPath,
        [string]$EvidenceRoot,
        [string]$BundlePath,
        [string]$Sha256Path,
        [string]$SummaryPath,
        [string]$SummaryJsonPath,
        [string]$VerifyReportJsonPath,
        [string]$VerifyReportMarkdownPath,
        [string]$BundleStatus,
        [string]$ReleaseCheckReportDirectory
    )

    $releaseCheck = Get-ReleaseCheckReportSummary -ReportDirectory $ReleaseCheckReportDirectory
    $verificationStatus = Get-ReleaseEvidenceVerificationReportStatus -VerifyReportJsonPath $VerifyReportJsonPath
    $gateReportVerificationStatus = Get-ReleaseEvidenceVerificationReportStatus -VerifyReportJsonPath (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.json")
    $artifacts = @(
        New-ReleaseEvidenceArtifactIndexEntry -Role "bundle" -Path $BundlePath -Status $BundleStatus
        New-ReleaseEvidenceArtifactIndexEntry -Role "bundleSha256" -Path $Sha256Path -Status "PRESENT"
        New-ReleaseEvidenceArtifactIndexEntry -Role "summaryMarkdown" -Path $SummaryPath -Status "PRESENT"
        New-ReleaseEvidenceArtifactIndexEntry -Role "summaryJson" -Path $SummaryJsonPath -Status "PRESENT"
        New-ReleaseEvidenceArtifactIndexEntry -Role "verificationReportJson" -Path $VerifyReportJsonPath -Status $verificationStatus
        New-ReleaseEvidenceArtifactIndexEntry -Role "verificationReportMarkdown" -Path $VerifyReportMarkdownPath -Status $verificationStatus
        New-ReleaseEvidenceArtifactIndexEntry -Role "preprodAcceptanceGateJson" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.json") -Status "PRESENT"
        New-ReleaseEvidenceArtifactIndexEntry -Role "preprodAcceptanceGateVerificationJson" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.json") -Status $gateReportVerificationStatus
        New-ReleaseEvidenceArtifactIndexEntry -Role "preprodAcceptanceGateVerificationMarkdown" -Path (Join-Path $EvidenceRoot "preprod-acceptance-gate.verify-report.md") -Status $gateReportVerificationStatus
    )
    $missingArtifactCount = @($artifacts | Where-Object { -not $_.exists }).Count
    $generatedAt = Get-Date -Format "o"

    $indexJson = [ordered]@{
        schemaVersion = 1
        generatedAt = $generatedAt
        evidenceDirectory = $EvidenceRoot
        bundlePath = $BundlePath
        bundleStatus = $BundleStatus
        allowBlocked = $AllowBlocked.IsPresent
        releaseCheck = [ordered]@{
            status = $releaseCheck.Status
            releaseCandidateCommit = $releaseCheck.ReleaseCandidateCommit
            allowDirtyWorktree = $releaseCheck.AllowDirtyWorktree
        }
        verificationStatus = $verificationStatus
        artifactCount = $artifacts.Count
        missingArtifactCount = $missingArtifactCount
        artifacts = @($artifacts)
    }

    $indexJsonDirectory = Split-Path -Path $IndexJsonPath -Parent
    if ($indexJsonDirectory -and -not (Test-Path -LiteralPath $indexJsonDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $indexJsonDirectory -Force | Out-Null
    }
    $indexJson | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $IndexJsonPath -Encoding UTF8

    $markdownLines = [System.Collections.Generic.List[string]]::new()
    $markdownLines.Add("# Release evidence artifacts index")
    $markdownLines.Add("")
    $markdownLines.Add("| Field | Value |")
    $markdownLines.Add("| --- | --- |")
    $markdownLines.Add("| Bundle status | $(Format-ReleaseEvidenceSummaryValue $BundleStatus) |")
    $markdownLines.Add("| Verification status | $(Format-ReleaseEvidenceSummaryValue $verificationStatus) |")
    $markdownLines.Add("| Release check status | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.Status) |")
    $markdownLines.Add("| Release candidate commit | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.ReleaseCandidateCommit) |")
    $markdownLines.Add("| Release check allow dirty worktree | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.AllowDirtyWorktree) |")
    $markdownLines.Add("| Artifact count | $($artifacts.Count) |")
    $markdownLines.Add("| Missing artifacts | $missingArtifactCount |")
    $markdownLines.Add("| Generated at | $generatedAt |")
    $markdownLines.Add("")
    $markdownLines.Add("| Artifact | Status | SHA-256 | Bytes | Path |")
    $markdownLines.Add("| --- | --- | --- | --- | --- |")
    foreach ($artifact in $artifacts) {
        $markdownLines.Add("| $(Format-ReleaseEvidenceSummaryValue $artifact.role) | $(Format-ReleaseEvidenceSummaryValue $artifact.status) | $(Format-ReleaseEvidenceSummaryValue $artifact.sha256) | $($artifact.length) | $(Format-ReleaseEvidenceSummaryValue $artifact.path) |")
    }

    $indexMarkdownDirectory = Split-Path -Path $IndexMarkdownPath -Parent
    if ($indexMarkdownDirectory -and -not (Test-Path -LiteralPath $indexMarkdownDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $indexMarkdownDirectory -Force | Out-Null
    }
    $markdownLines | Set-Content -LiteralPath $IndexMarkdownPath -Encoding UTF8
}

function Copy-ReleaseEvidenceFileToStaging {
    param(
        [System.IO.FileInfo]$File,
        [string]$RelativePath,
        [string]$StagingRoot
    )

    $targetPath = Join-Path $StagingRoot $RelativePath
    $targetDirectory = Split-Path -Path $targetPath -Parent
    if ($targetDirectory -and -not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
    }
    Copy-Item -LiteralPath $File.FullName -Destination $targetPath -Force
}

function Invoke-ReleaseEvidenceBundleVerifier {
    param(
        [string]$BundlePath,
        [string]$Sha256Path,
        [switch]$AllowBlocked
    )

    $verifierPath = Join-Path $PSScriptRoot "verify-release-evidence-bundle.ps1"
    if (-not (Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        throw "Missing release evidence bundle verifier: $verifierPath"
    }

    $verifierParams = @{ BundlePath = $BundlePath; Sha256Path = $Sha256Path }
    $verifierDisplayArgs = @("-BundlePath", $BundlePath, "-Sha256Path", $Sha256Path)
    if ($AllowBlocked) {
        $verifierParams.AllowBlocked = $true
        $verifierDisplayArgs += "-AllowBlocked"
    }

    [System.Console]::WriteLine("[release-evidence-bundle] Release evidence bundle self-verification: verify-release-evidence-bundle.ps1 $($verifierDisplayArgs -join ' ')")
    & $verifierPath @verifierParams
}

function Invoke-ReleaseEvidenceArtifactsIndexVerifier {
    param([string]$ArtifactsIndexPath)

    $verifierPath = Join-Path $PSScriptRoot "verify-release-evidence-artifacts-index.ps1"
    if (-not (Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        throw "Missing release evidence artifacts index verifier: $verifierPath"
    }

    [System.Console]::WriteLine("[release-evidence-bundle] Release evidence artifacts index verification: verify-release-evidence-artifacts-index.ps1 -ArtifactsIndexPath $ArtifactsIndexPath")
    & $verifierPath -ArtifactsIndexPath $ArtifactsIndexPath
}

$indexPath = Get-ReleaseEvidenceIndexPath
$evidenceRoot = Get-ReleaseEvidenceDirectory -IndexPath $indexPath
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path $evidenceRoot "release-evidence-bundle-$stamp.zip"
}
if (-not $OutputPath.EndsWith(".zip", [System.StringComparison]::OrdinalIgnoreCase)) {
    $OutputPath = "$OutputPath.zip"
}

$index = Read-ReleaseEvidenceIndex -IndexPath $indexPath
Assert-ReleaseEvidenceBundlePrerequisites -Index $index -IndexPath $indexPath -EvidenceRoot $evidenceRoot
$releaseCheckReportFiles = @(Assert-ReleaseCheckReports -ReportDirectory $ReleaseCheckReportDirectory)
Assert-ReleaseEvidenceCandidateCommit -Index $index -ReportDirectory $ReleaseCheckReportDirectory

$bundleStatus = "READY"
if ($bundleFailureCount -gt 0) {
    $bundleStatus = "BLOCKED"
}
if ($bundleFailureCount -gt 0 -and -not $AllowBlocked) {
    throw "Release evidence bundle prerequisites failed with $bundleFailureCount failed check(s). Use -AllowBlocked only when archiving a blocked release investigation package."
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$evidenceSourceFiles = @(Get-ChildItem -Path $evidenceRoot -File -Recurse | Where-Object {
        $fullName = [System.IO.Path]::GetFullPath($_.FullName)
        $fullName -ne $outputFullPath -and
        $fullName -ne "$outputFullPath.sha256" -and
        $fullName -ne "$outputFullPath.summary.md" -and
        $fullName -ne "$outputFullPath.summary.json" -and
        $_.Name -notlike "release-evidence-bundle*.zip" -and
        $_.Name -notlike "release-evidence-bundle*.zip.sha256" -and
        $_.Name -notlike "release-evidence-bundle*.zip.summary.md" -and
        $_.Name -notlike "release-evidence-bundle*.zip.summary.json" -and
        $_.Name -ne "release-evidence-artifacts-index.json" -and
        $_.Name -ne "release-evidence-artifacts-index.md" -and
        $_.Name -ne "release-evidence-artifacts-index.verify-report.json" -and
        $_.Name -ne "release-evidence-artifacts-index.verify-report.md"
    })
if ($evidenceSourceFiles.Count -eq 0) {
    throw "No release evidence files were found under $evidenceRoot."
}

$bundleFiles = [System.Collections.Generic.List[object]]::new()
foreach ($file in $evidenceSourceFiles) {
    $bundleFiles.Add([pscustomobject]@{
        File = $file
        RelativePath = Get-ReleaseEvidenceRelativePath -Root $evidenceRoot -Path $file.FullName
    })
}
foreach ($file in $releaseCheckReportFiles) {
    $bundleFiles.Add([pscustomobject]@{
        File = $file
        RelativePath = "release-check/$($file.Name)"
    })
}

$sourceEntries = @($bundleFiles | ForEach-Object { New-ReleaseEvidenceSourceFile -RelativePath $_.RelativePath -File $_.File })
$stagingRoot = Join-Path ([System.IO.Path]::GetTempPath()) "release-evidence-bundle-$([System.Guid]::NewGuid().ToString("N"))"
New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
try {
    foreach ($entry in $bundleFiles) {
        Copy-ReleaseEvidenceFileToStaging -File $entry.File -RelativePath $entry.RelativePath -StagingRoot $stagingRoot
    }

    $manifestPath = Join-Path $stagingRoot "release-evidence-bundle-manifest.json"
    Save-ReleaseEvidenceBundleManifest -ManifestPath $manifestPath -EvidenceRoot $evidenceRoot -IndexPath $indexPath -BundleStatus $bundleStatus -SourceFiles $sourceEntries

    $outputDirectory = Split-Path -Path $OutputPath -Parent
    if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    }
    Compress-Archive -Path (Join-Path $stagingRoot "*") -DestinationPath $OutputPath -Force
}
finally {
    if (Test-Path -LiteralPath $stagingRoot -PathType Container) {
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
}

$bundleHash = [pscustomobject]@{ Hash = (Get-Sha256Hex -LiteralPath $OutputPath) }
$sha256Path = "$OutputPath.sha256"
"$($bundleHash.Hash)  $(Split-Path -Path $OutputPath -Leaf)" | Set-Content -LiteralPath $sha256Path -Encoding ASCII

$summaryPath = "$OutputPath.summary.md"
$summaryJsonPath = "$OutputPath.summary.json"
Save-ReleaseEvidenceBundleSummary -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath -BundlePath $OutputPath -Sha256Path $sha256Path -BundleStatus $bundleStatus -BundleSha256 $bundleHash.Hash -SourceFileCount $sourceEntries.Count -ReleaseCheckReportDirectory $ReleaseCheckReportDirectory
Invoke-ReleaseEvidenceBundleVerifier -BundlePath $OutputPath -Sha256Path $sha256Path -AllowBlocked:$AllowBlocked
$artifactsIndexJsonPath = Join-Path $evidenceRoot "release-evidence-artifacts-index.json"
$artifactsIndexMarkdownPath = Join-Path $evidenceRoot "release-evidence-artifacts-index.md"
$verifyReportJsonPath = "$OutputPath.verify-report.json"
$verifyReportMarkdownPath = "$OutputPath.verify-report.md"
Save-ReleaseEvidenceArtifactsIndex -IndexJsonPath $artifactsIndexJsonPath -IndexMarkdownPath $artifactsIndexMarkdownPath -BundlePath $OutputPath -Sha256Path $sha256Path -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath -VerifyReportJsonPath $verifyReportJsonPath -VerifyReportMarkdownPath $verifyReportMarkdownPath -BundleStatus $bundleStatus -EvidenceRoot $evidenceRoot -ReleaseCheckReportDirectory $ReleaseCheckReportDirectory
Invoke-ReleaseEvidenceArtifactsIndexVerifier -ArtifactsIndexPath $artifactsIndexJsonPath

[System.Console]::WriteLine("[release-evidence-bundle] Bundle written to $OutputPath")
[System.Console]::WriteLine("[release-evidence-bundle] SHA-256 written to $sha256Path")
[System.Console]::WriteLine("[release-evidence-bundle] Summary written to $summaryPath")
[System.Console]::WriteLine("[release-evidence-bundle] Summary JSON written to $summaryJsonPath")
[System.Console]::WriteLine("[release-evidence-bundle] Artifacts index JSON written to $artifactsIndexJsonPath")
[System.Console]::WriteLine("[release-evidence-bundle] Artifacts index Markdown written to $artifactsIndexMarkdownPath")
[System.Console]::WriteLine("[release-evidence-bundle] Bundle status: $bundleStatus")

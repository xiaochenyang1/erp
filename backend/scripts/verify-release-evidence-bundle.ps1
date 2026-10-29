param(
    [string]$BundlePath,
    [string]$Sha256Path,
    [string]$ExtractDirectory,
    [switch]$AllowBlocked
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sha256-helpers.ps1")

$verificationChecks = [System.Collections.Generic.List[object]]::new()
$verificationFailureCount = 0

function Add-ReleaseEvidenceBundleVerificationCheck {
    param(
        [string]$Name,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status,
        [string]$Detail
    )

    $script:verificationChecks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    [System.Console]::WriteLine("[release-evidence-bundle-verify] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:verificationFailureCount++
    }
}

function Get-ReleaseEvidenceBundlePath {
    if ([string]::IsNullOrWhiteSpace($BundlePath)) {
        throw "Provide -BundlePath."
    }
    if (-not (Test-Path -LiteralPath $BundlePath -PathType Leaf)) {
        throw "BundlePath does not exist: $BundlePath"
    }
    return (Resolve-Path -LiteralPath $BundlePath).Path
}

function Get-ReleaseEvidenceBundleSha256Path {
    param([string]$ResolvedBundlePath)

    if (-not [string]::IsNullOrWhiteSpace($Sha256Path)) {
        return $Sha256Path
    }

    return "$ResolvedBundlePath.sha256"
}

function Assert-ReleaseEvidenceBundleSha256 {
    param(
        [string]$ResolvedBundlePath,
        [string]$ResolvedSha256Path
    )

    if (-not (Test-Path -LiteralPath $ResolvedSha256Path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256 file" "FAILED" "SHA-256 file does not exist: $ResolvedSha256Path"
        return
    }

    $sha256Content = (Get-Content -LiteralPath $ResolvedSha256Path -Raw).Trim()
    $sha256Match = [regex]::Match($sha256Content, "^(?<hash>[A-Fa-f0-9]{64})(\s+(?<fileName>.+))?$")
    if (-not $sha256Match.Success) {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256 file" "FAILED" "SHA-256 file format is invalid: $ResolvedSha256Path"
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256 file" "PASSED" "SHA-256 file exists and has a hash."
    $expectedBundleFileName = Split-Path -Path $ResolvedBundlePath -Leaf
    $sidecarBundleFileName = $sha256Match.Groups["fileName"].Value.Trim()
    if (-not [string]::IsNullOrWhiteSpace($sidecarBundleFileName)) {
        $sidecarBundleFileName = [System.IO.Path]::GetFileName($sidecarBundleFileName)
        if ($sidecarBundleFileName -eq $expectedBundleFileName) {
            Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256 file name" "PASSED" "SHA-256 sidecar names the verified bundle '$expectedBundleFileName'."
        }
        else {
            Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256 file name" "FAILED" "SHA-256 sidecar names '$sidecarBundleFileName', expected '$expectedBundleFileName'."
        }
    }

    $expectedHash = $sha256Match.Groups["hash"].Value.ToUpperInvariant()
    $actualHash = (Get-Sha256Hex -LiteralPath $ResolvedBundlePath).ToUpperInvariant()
    if ($actualHash -eq $expectedHash) {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256" "PASSED" "Bundle hash matches $ResolvedSha256Path."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle SHA-256" "FAILED" "Expected $expectedHash but found $actualHash."
    }

    return $actualHash
}

function New-ReleaseEvidenceBundleExtractDirectory {
    if ([string]::IsNullOrWhiteSpace($ExtractDirectory)) {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "release-evidence-bundle-verify-$([System.Guid]::NewGuid().ToString("N"))"
        New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
        return [pscustomobject]@{
            Path = $tempRoot
            Temporary = $true
        }
    }

    $extractRoot = [System.IO.Path]::GetFullPath($ExtractDirectory)
    if (Test-Path -LiteralPath $extractRoot -PathType Container) {
        $existingFiles = @(Get-ChildItem -LiteralPath $extractRoot -Force -ErrorAction SilentlyContinue)
        if ($existingFiles.Count -gt 0) {
            throw "ExtractDirectory must be empty before verification: $extractRoot"
        }
    }
    else {
        New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    }

    return [pscustomobject]@{
        Path = $extractRoot
        Temporary = $false
    }
}

function Expand-ReleaseEvidenceBundle {
    param(
        [string]$ResolvedBundlePath,
        [string]$ExtractRoot
    )

    Expand-Archive -LiteralPath $ResolvedBundlePath -DestinationPath $ExtractRoot -Force
    Add-ReleaseEvidenceBundleVerificationCheck "Bundle archive" "PASSED" "Expanded bundle to $ExtractRoot."
}

function Get-ReleaseEvidenceBundleRelativePath {
    param(
        [string]$Root,
        [string]$Path
    )

    $rootFull = [System.IO.Path]::GetFullPath($Root).TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    $pathFull = [System.IO.Path]::GetFullPath($Path)
    if ($pathFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $pathFull.Substring($rootFull.Length).Replace("\", "/")
    }

    return [System.IO.Path]::GetFileName($pathFull).Replace("\", "/")
}

function Resolve-ReleaseEvidenceBundleExtractedPath {
    param(
        [string]$ExtractRoot,
        [string]$RelativePath
    )

    $rootFull = [System.IO.Path]::GetFullPath($ExtractRoot).TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    $pathFull = [System.IO.Path]::GetFullPath((Join-Path $ExtractRoot $RelativePath))
    if (-not $pathFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $null
    }

    return $pathFull
}

function Get-ReleaseEvidenceBundleObjectProperty {
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

function Assert-ReleaseEvidenceBundleCandidateCommit {
    param([string]$ExtractRoot)

    $indexPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath "evidence-index.json"
    if ([string]::IsNullOrWhiteSpace($indexPath) -or -not (Test-Path -LiteralPath $indexPath -PathType Leaf)) {
        # Older bundles predate candidate binding and do not carry an evidence index.
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index candidate binding" "PASSED" "evidence-index.json is absent; candidate binding check was skipped for a legacy bundle."
        return
    }

    try {
        $index = Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index candidate binding" "PASSED" "Parsed bundled evidence-index.json."
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index candidate binding" "FAILED" "Cannot parse bundled evidence-index.json: $(($_ | Out-String).Trim())"
        return
    }

    $releaseCheck = Get-ReleaseEvidenceBundleObjectProperty -Object $index -Name "releaseCheck"
    if ($null -eq $releaseCheck) {
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index candidate binding" "PASSED" "Bundled evidence index has no releaseCheck binding; legacy candidate check was skipped."
        return
    }

    $indexCandidate = Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheck -Name "releaseCandidateCommit"
    if ([string]::IsNullOrWhiteSpace([string]$indexCandidate)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index releaseCandidateCommit" "FAILED" "Bundled evidence index releaseCheck.releaseCandidateCommit is missing or blank."
        return
    }
    $indexCandidate = ([string]$indexCandidate).Trim()

    $reportPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath "release-check/release-check-report.json"
    if ([string]::IsNullOrWhiteSpace($reportPath) -or -not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index releaseCandidateCommit" "FAILED" "Cannot compare bundled candidate: release-check/release-check-report.json is missing."
        return
    }

    try {
        $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Evidence index releaseCandidateCommit" "FAILED" "Cannot parse bundled release-check report for candidate binding: $(($_ | Out-String).Trim())"
        return
    }

    $reportCandidate = Get-ReleaseEvidenceBundleObjectProperty -Object $report -Name "releaseCandidateCommit"
    if ([string]::IsNullOrWhiteSpace([string]$reportCandidate)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Release-check releaseCandidateCommit" "FAILED" "Bundled release-check report releaseCandidateCommit is missing or blank."
        return
    }
    $reportCandidate = ([string]$reportCandidate).Trim()

    if ($indexCandidate.Equals($reportCandidate, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-ReleaseEvidenceBundleVerificationCheck "releaseCandidateCommit consistency" "PASSED" "Bundled evidence index candidate matches bundled release-check report candidate $reportCandidate."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "releaseCandidateCommit consistency" "FAILED" "Bundled evidence index candidate $indexCandidate does not match bundled release-check report candidate $reportCandidate."
    }
}

function Get-ReleaseEvidenceBundleMarkdownTableValue {
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

function Assert-ReleaseEvidenceBundleRequiredText {
    param(
        [string]$Name,
        [string]$ExtractRoot,
        [string]$RelativePath,
        [string]$RequiredText
    )

    $path = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $RelativePath
    if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "Required file is missing from bundle: $RelativePath"
        return
    }

    $content = Get-Content -LiteralPath $path -Raw
    if ($content.Contains($RequiredText)) {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "PASSED" "Found required text '$RequiredText' in $RelativePath."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "Required text '$RequiredText' was not found in $RelativePath."
}

function Assert-ReleaseEvidenceBundleRequiredJsonProperty {
    param(
        [string]$Name,
        [string]$ExtractRoot,
        [string]$RelativePath,
        [string]$PropertyName,
        [string]$ExpectedValue
    )

    $path = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $RelativePath
    if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "Required JSON file is missing from bundle: $RelativePath"
        return
    }

    try {
        $json = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "Cannot parse ${RelativePath}: $(($_ | Out-String).Trim())"
        return
    }

    $actualValue = Get-ReleaseEvidenceBundleObjectProperty -Object $json -Name $PropertyName
    if ($null -eq $actualValue) {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "JSON property '$PropertyName' is missing in $RelativePath."
        return
    }

    $actualText = [string]$actualValue
    if ($actualText -eq $ExpectedValue) {
        Add-ReleaseEvidenceBundleVerificationCheck $Name "PASSED" "JSON property '$PropertyName' is '$ExpectedValue' in $RelativePath."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck $Name "FAILED" "JSON property '$PropertyName' is '$actualText' in $RelativePath, expected '$ExpectedValue'."
}

function Assert-ReleaseEvidenceBundleReleaseCheckDirtyWorktreePolicy {
    param([string]$ExtractRoot)

    $relativePath = "release-check/release-check-report.json"
    $path = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $relativePath
    if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Release check report dirty worktree policy" "FAILED" "Required JSON file is missing from bundle: $relativePath"
        return
    }

    try {
        $releaseCheckReport = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Release check report dirty worktree policy" "FAILED" "Cannot parse ${relativePath}: $(($_ | Out-String).Trim())"
        return
    }

    $reportStatus = ([string](Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "status")).ToUpperInvariant()
    $reportAllowsDirtyWorktree = [bool](Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "allowDirtyWorktree")
    if ($reportStatus -eq "PASSED" -and $reportAllowsDirtyWorktree) {
        Add-ReleaseEvidenceBundleVerificationCheck "Release check report dirty worktree policy" "FAILED" "release-check PASSED report was generated with -AllowDirtyWorktree; dirty worktree reports are local non-release investigation evidence only."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Release check report dirty worktree policy" "PASSED" "release-check dirty worktree policy accepted."
}

function Assert-ReleaseEvidenceBundlePreprodGateVerificationMarkdownStatus {
    param([string]$ExtractRoot)

    $jsonRelativePath = "preprod-acceptance-gate.verify-report.json"
    $markdownRelativePath = "preprod-acceptance-gate.verify-report.md"
    $jsonPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $jsonRelativePath
    if ([string]::IsNullOrWhiteSpace($jsonPath) -or -not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification JSON report is missing from bundle: $jsonRelativePath"
        return
    }

    $markdownPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $markdownRelativePath
    if ([string]::IsNullOrWhiteSpace($markdownPath) -or -not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown report is missing from bundle: $markdownRelativePath"
        return
    }

    try {
        $json = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Cannot parse ${jsonRelativePath}: $(($_ | Out-String).Trim())"
        return
    }

    $expectedStatus = [string](Get-ReleaseEvidenceBundleObjectProperty -Object $json -Name "status")
    if ([string]::IsNullOrWhiteSpace($expectedStatus)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification JSON status is missing."
        return
    }

    $markdown = Get-Content -LiteralPath $markdownPath -Raw
    $actualStatus = Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $markdown -Field "Status"
    if ($null -eq $actualStatus -or [string]::IsNullOrWhiteSpace([string]$actualStatus)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown Status field is missing."
        return
    }

    $actualText = [string]$actualStatus
    if ($actualText.ToUpperInvariant() -eq $expectedStatus.ToUpperInvariant()) {
        Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "PASSED" "Gate verification Markdown Status matches JSON status '$expectedStatus'."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Preproduction approval gate report verification Markdown status" "FAILED" "Gate verification Markdown Status is '$actualText', expected '$expectedStatus'."
}

function Assert-ReleaseEvidenceBundleRequiredEvidence {
    param(
        [string]$ExtractRoot,
        [object]$Manifest
    )

    $bundleStatus = if ($null -eq $Manifest) { "" } else { ([string]$Manifest.bundleStatus).ToUpperInvariant() }
    if ($bundleStatus -eq "BLOCKED" -and $AllowBlocked) {
        Add-ReleaseEvidenceBundleVerificationCheck "Required release evidence semantics" "PASSED" "Strict READY evidence semantics are skipped for a BLOCKED package when -AllowBlocked is specified."
        return
    }

    Assert-ReleaseEvidenceBundleRequiredText -Name "Preproduction approval gate report" -ExtractRoot $ExtractRoot -RelativePath "preprod-acceptance-gate.md" -RequiredText "READY_FOR_APPROVAL"
    Assert-ReleaseEvidenceBundleRequiredJsonProperty -Name "Preproduction approval gate JSON verdict" -ExtractRoot $ExtractRoot -RelativePath "preprod-acceptance-gate.json" -PropertyName "verdict" -ExpectedValue "READY_FOR_APPROVAL"
    Assert-ReleaseEvidenceBundleRequiredJsonProperty -Name "Preproduction approval gate report verification JSON status" -ExtractRoot $ExtractRoot -RelativePath "preprod-acceptance-gate.verify-report.json" -PropertyName "status" -ExpectedValue "PASSED"
    Assert-ReleaseEvidenceBundleRequiredText -Name "Preproduction approval gate report verification Markdown" -ExtractRoot $ExtractRoot -RelativePath "preprod-acceptance-gate.verify-report.md" -RequiredText "Preproduction acceptance gate report verification"
    Assert-ReleaseEvidenceBundlePreprodGateVerificationMarkdownStatus -ExtractRoot $ExtractRoot
    Assert-ReleaseEvidenceBundleRequiredText -Name "Readiness release decision report" -ExtractRoot $ExtractRoot -RelativePath "readiness-release-decision.md" -RequiredText "DECIDED_GO"
    Assert-ReleaseEvidenceBundleRequiredJsonProperty -Name "Release check report status" -ExtractRoot $ExtractRoot -RelativePath "release-check/release-check-report.json" -PropertyName "status" -ExpectedValue "PASSED"
    Assert-ReleaseEvidenceBundleReleaseCheckDirtyWorktreePolicy -ExtractRoot $ExtractRoot
    Assert-ReleaseEvidenceBundleRequiredText -Name "Release check report Markdown" -ExtractRoot $ExtractRoot -RelativePath "release-check/release-check-report.md" -RequiredText "Release Check Report"
}

function Assert-ReleaseEvidenceBundleManifest {
    param([string]$ExtractRoot)

    $manifestPath = Join-Path $ExtractRoot "release-evidence-bundle-manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle manifest" "FAILED" "release-evidence-bundle-manifest.json was not found."
        return $null
    }

    try {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Bundle manifest JSON" "FAILED" "Cannot parse release-evidence-bundle-manifest.json: $(($_ | Out-String).Trim())"
        return $null
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Bundle manifest" "PASSED" "Parsed release-evidence-bundle-manifest.json."
    $bundleStatus = ([string]$manifest.bundleStatus).ToUpperInvariant()
    if ($bundleStatus -eq "READY") {
        Add-ReleaseEvidenceBundleVerificationCheck "bundleStatus" "PASSED" "bundleStatus is READY."
    }
    elseif ($bundleStatus -eq "BLOCKED" -and $AllowBlocked) {
        Add-ReleaseEvidenceBundleVerificationCheck "bundleStatus" "PASSED" "bundleStatus is BLOCKED and -AllowBlocked was specified."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "bundleStatus" "FAILED" "bundleStatus is '$bundleStatus'; expected READY unless -AllowBlocked is used for BLOCKED packages."
    }

    $sourceFiles = @($manifest.sourceFiles)
    if ($sourceFiles.Count -gt 0) {
        Add-ReleaseEvidenceBundleVerificationCheck "sourceFiles" "PASSED" "Manifest lists $($sourceFiles.Count) source file(s)."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "sourceFiles" "FAILED" "Manifest sourceFiles is empty."
    }

    $manifestChecks = @($manifest.checks)
    $failedManifestChecks = @($manifestChecks | Where-Object { ([string]$_.Status).ToUpperInvariant() -eq "FAILED" })
    if ($failedManifestChecks.Count -eq 0) {
        Add-ReleaseEvidenceBundleVerificationCheck "Manifest prerequisite checks" "PASSED" "All manifest checks are PASSED."
    }
    elseif ($AllowBlocked) {
        Add-ReleaseEvidenceBundleVerificationCheck "Manifest prerequisite checks" "PASSED" "Manifest contains $($failedManifestChecks.Count) failed check(s), allowed for a BLOCKED package."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Manifest prerequisite checks" "FAILED" "Manifest contains $($failedManifestChecks.Count) failed check(s); use -AllowBlocked only for blocked release investigation packages."
    }

    return $manifest
}

function Assert-ReleaseEvidenceBundleSourceFiles {
    param(
        [string]$ExtractRoot,
        [object]$Manifest
    )

    if ($null -eq $Manifest) {
        return
    }

    $expectedPaths = @{}
    foreach ($sourceFile in @($Manifest.sourceFiles)) {
        $relativePath = ([string]$sourceFile.relativePath).Replace("\", "/")
        if ([string]::IsNullOrWhiteSpace($relativePath)) {
            Add-ReleaseEvidenceBundleVerificationCheck "Source file entry" "FAILED" "A sourceFiles entry is missing relativePath."
            continue
        }

        $expectedPaths[$relativePath] = $true
        $extractedPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath $relativePath
        if ([string]::IsNullOrWhiteSpace($extractedPath)) {
            Add-ReleaseEvidenceBundleVerificationCheck "Source path $relativePath" "FAILED" "relativePath escapes the extracted bundle root."
            continue
        }
        if (-not (Test-Path -LiteralPath $extractedPath -PathType Leaf)) {
            Add-ReleaseEvidenceBundleVerificationCheck "Source file $relativePath" "FAILED" "File is missing from the extracted bundle."
            continue
        }

        $fileInfo = Get-Item -LiteralPath $extractedPath
        $expectedLength = [long]$sourceFile.length
        if ($fileInfo.Length -eq $expectedLength) {
            Add-ReleaseEvidenceBundleVerificationCheck "Source length $relativePath" "PASSED" "Length matches $expectedLength."
        }
        else {
            Add-ReleaseEvidenceBundleVerificationCheck "Source length $relativePath" "FAILED" "Expected $expectedLength byte(s) but found $($fileInfo.Length)."
        }

        $expectedHash = ([string]$sourceFile.sha256).ToUpperInvariant()
        $actualHash = (Get-Sha256Hex -LiteralPath $extractedPath).ToUpperInvariant()
        if ($actualHash -eq $expectedHash) {
            Add-ReleaseEvidenceBundleVerificationCheck "Source SHA-256 $relativePath" "PASSED" "SHA-256 matches manifest."
        }
        else {
            Add-ReleaseEvidenceBundleVerificationCheck "Source SHA-256 $relativePath" "FAILED" "Expected $expectedHash but found $actualHash."
        }
    }

    $actualFiles = @(Get-ChildItem -LiteralPath $ExtractRoot -File -Recurse | Where-Object {
            (Get-ReleaseEvidenceBundleRelativePath -Root $ExtractRoot -Path $_.FullName) -ne "release-evidence-bundle-manifest.json"
        })
    foreach ($actualFile in $actualFiles) {
        $relativePath = Get-ReleaseEvidenceBundleRelativePath -Root $ExtractRoot -Path $actualFile.FullName
        if (-not $expectedPaths.ContainsKey($relativePath)) {
            Add-ReleaseEvidenceBundleVerificationCheck "Unexpected source file $relativePath" "FAILED" "File exists in the bundle but is not listed in manifest sourceFiles."
        }
    }
}

function Assert-ReleaseEvidenceBundleSummaryJson {
    param(
        [string]$ResolvedBundlePath,
        [string]$ExtractRoot,
        [object]$Manifest,
        [string]$ExpectedBundleSha256
    )

    $summaryJsonPath = "$ResolvedBundlePath.summary.json"
    if (-not (Test-Path -LiteralPath $summaryJsonPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON" "FAILED" "Summary JSON does not exist: $summaryJsonPath"
        return
    }

    try {
        $summaryJson = Get-Content -LiteralPath $summaryJsonPath -Raw | ConvertFrom-Json
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON" "PASSED" "Parsed $summaryJsonPath."
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON" "FAILED" "Cannot parse $($summaryJsonPath): $(($_ | Out-String).Trim())"
        return
    }

    if ([int]$summaryJson.schemaVersion -eq 1) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON schemaVersion" "PASSED" "schemaVersion is 1."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON schemaVersion" "FAILED" "schemaVersion is '$($summaryJson.schemaVersion)', expected 1."
    }

    if ($null -eq $Manifest) {
        return
    }

    $expectedBundleStatus = ([string]$Manifest.bundleStatus).ToUpperInvariant()
    $actualBundleStatus = ([string]$summaryJson.bundleStatus).ToUpperInvariant()
    if ($actualBundleStatus -eq $expectedBundleStatus) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON bundleStatus" "PASSED" "bundleStatus matches manifest."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON bundleStatus" "FAILED" "Expected $expectedBundleStatus but found $actualBundleStatus."
    }

    $actualBundleSha256 = ([string]$summaryJson.bundleSha256).ToUpperInvariant()
    if ($actualBundleSha256 -eq $ExpectedBundleSha256.ToUpperInvariant()) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON bundleSha256" "PASSED" "bundleSha256 matches bundle hash."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON bundleSha256" "FAILED" "Expected $ExpectedBundleSha256 but found $actualBundleSha256."
    }

    $expectedSourceFileCount = @($Manifest.sourceFiles).Count
    if ([int]$summaryJson.sourceFileCount -eq $expectedSourceFileCount) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON sourceFileCount" "PASSED" "sourceFileCount matches manifest."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON sourceFileCount" "FAILED" "Expected $expectedSourceFileCount but found $($summaryJson.sourceFileCount)."
    }

    $failedManifestChecks = @(@($Manifest.checks) | Where-Object { ([string]$_.Status).ToUpperInvariant() -eq "FAILED" }).Count
    if ([int]$summaryJson.failedPrerequisiteCheckCount -eq $failedManifestChecks) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON failedPrerequisiteCheckCount" "PASSED" "failedPrerequisiteCheckCount matches manifest checks."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON failedPrerequisiteCheckCount" "FAILED" "Expected $failedManifestChecks but found $($summaryJson.failedPrerequisiteCheckCount)."
    }

    $releaseCheckReportPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath "release-check/release-check-report.json"
    if ([string]::IsNullOrWhiteSpace($releaseCheckReportPath) -or -not (Test-Path -LiteralPath $releaseCheckReportPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.status" "FAILED" "release-check/release-check-report.json was not found in bundle."
        return
    }

    try {
        $releaseCheckReport = Get-Content -LiteralPath $releaseCheckReportPath -Raw | ConvertFrom-Json
    }
    catch {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.status" "FAILED" "Cannot parse release-check report: $(($_ | Out-String).Trim())"
        return
    }

    if ([string]$summaryJson.releaseCheck.status -eq [string]$releaseCheckReport.status) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.status" "PASSED" "releaseCheck.status matches bundled release-check report."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.status" "FAILED" "Expected $($releaseCheckReport.status) but found $($summaryJson.releaseCheck.status)."
    }

    if ([string]$summaryJson.releaseCheck.releaseCandidateCommit -eq [string]$releaseCheckReport.releaseCandidateCommit) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.releaseCandidateCommit" "PASSED" "releaseCandidateCommit matches bundled release-check report."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.releaseCandidateCommit" "FAILED" "Expected $($releaseCheckReport.releaseCandidateCommit) but found $($summaryJson.releaseCheck.releaseCandidateCommit)."
    }

    $expectedAllowDirtyWorktree = [string](Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "allowDirtyWorktree")
    $actualAllowDirtyWorktree = [string](Get-ReleaseEvidenceBundleObjectProperty -Object $summaryJson.releaseCheck -Name "allowDirtyWorktree")
    if ($actualAllowDirtyWorktree -eq $expectedAllowDirtyWorktree) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.allowDirtyWorktree" "PASSED" "allowDirtyWorktree matches bundled release-check report."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary JSON releaseCheck.allowDirtyWorktree" "FAILED" "Expected $expectedAllowDirtyWorktree but found $actualAllowDirtyWorktree."
    }
}

function Assert-ReleaseEvidenceBundleSummaryMarkdownValue {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedValue
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedValue)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Expected Summary Markdown field value is missing."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Summary Markdown field '$Field' is missing."
        return
    }

    $actualText = [string]$ActualValue
    if ($actualText.ToUpperInvariant() -eq $ExpectedValue.ToUpperInvariant()) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "PASSED" "Summary Markdown field '$Field' is '$actualText'."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Summary Markdown field '$Field' is '$actualText', expected '$ExpectedValue'."
}

function Resolve-ReleaseEvidenceBundleSummaryPathValue {
    param(
        [string]$PathValue,
        [string]$SummaryDirectory
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }

    if (-not [string]::IsNullOrWhiteSpace($SummaryDirectory)) {
        $summaryRelativePath = Join-Path $SummaryDirectory $PathValue
        if (Test-Path -LiteralPath $summaryRelativePath -PathType Leaf) {
            return [System.IO.Path]::GetFullPath($summaryRelativePath)
        }
    }

    return [System.IO.Path]::GetFullPath($PathValue)
}

function Assert-ReleaseEvidenceBundleSummaryMarkdownPathValue {
    param(
        [string]$Field,
        [object]$ActualValue,
        [string]$ExpectedPath,
        [string]$SummaryDirectory
    )

    if ([string]::IsNullOrWhiteSpace($ExpectedPath)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Expected Summary Markdown path is missing."
        return
    }

    if ($null -eq $ActualValue -or [string]::IsNullOrWhiteSpace([string]$ActualValue)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Summary Markdown field '$Field' is missing."
        return
    }

    $actualText = [string]$ActualValue
    $actualPath = Resolve-ReleaseEvidenceBundleSummaryPathValue -PathValue $actualText -SummaryDirectory $SummaryDirectory
    $expectedFullPath = [System.IO.Path]::GetFullPath($ExpectedPath)
    if ($actualPath -eq $expectedFullPath) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "PASSED" "Summary Markdown field '$Field' points to '$actualText'."
        return
    }

    Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown field $Field" "FAILED" "Summary Markdown field '$Field' points to '$actualText', expected '$expectedFullPath'."
}

function Assert-ReleaseEvidenceBundleSummaryMarkdown {
    param(
        [string]$ResolvedBundlePath,
        [string]$ResolvedSha256Path,
        [string]$ExtractRoot,
        [object]$Manifest,
        [string]$ExpectedBundleSha256
    )

    $summaryMarkdownPath = "$ResolvedBundlePath.summary.md"
    if (-not (Test-Path -LiteralPath $summaryMarkdownPath -PathType Leaf)) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown" "FAILED" "Summary Markdown does not exist: $summaryMarkdownPath"
        return
    }

    $summaryMarkdown = Get-Content -LiteralPath $summaryMarkdownPath -Raw
    Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown" "PASSED" "Found $summaryMarkdownPath."

    if ($null -eq $Manifest) {
        return
    }

    $expectedBundleStatus = ([string]$Manifest.bundleStatus).ToUpperInvariant()
    $expectedSourceFileCount = @($Manifest.sourceFiles).Count
    $failedManifestChecks = @(@($Manifest.checks) | Where-Object { ([string]$_.Status).ToUpperInvariant() -eq "FAILED" }).Count
    $releaseCheckReportPath = Resolve-ReleaseEvidenceBundleExtractedPath -ExtractRoot $ExtractRoot -RelativePath "release-check/release-check-report.json"
    $expectedReleaseCheckStatus = "UNKNOWN"
    $expectedReleaseCandidateCommit = "UNKNOWN"
    $expectedAllowDirtyWorktree = "UNKNOWN"
    if (-not [string]::IsNullOrWhiteSpace($releaseCheckReportPath) -and (Test-Path -LiteralPath $releaseCheckReportPath -PathType Leaf)) {
        try {
            $releaseCheckReport = Get-Content -LiteralPath $releaseCheckReportPath -Raw | ConvertFrom-Json
            $releaseCheckStatus = Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "status"
            $releaseCandidateCommit = Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "releaseCandidateCommit"
            if (-not [string]::IsNullOrWhiteSpace([string]$releaseCheckStatus)) {
                $expectedReleaseCheckStatus = [string]$releaseCheckStatus
            }
            if (-not [string]::IsNullOrWhiteSpace([string]$releaseCandidateCommit)) {
                $expectedReleaseCandidateCommit = [string]$releaseCandidateCommit
            }
            $expectedAllowDirtyWorktree = [string](Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name "allowDirtyWorktree")
        }
        catch {
            Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown releaseCheck.allowDirtyWorktree" "FAILED" "Cannot parse release-check report: $(($_ | Out-String).Trim())"
        }
    }
    if ($summaryMarkdown.Contains("Release evidence bundle summary")) {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown title" "PASSED" "Found release evidence bundle summary title."
    }
    else {
        Add-ReleaseEvidenceBundleVerificationCheck "Summary Markdown title" "FAILED" "Release evidence bundle summary title was not found."
    }

    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Bundle status" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Bundle status") -ExpectedValue $expectedBundleStatus
    Assert-ReleaseEvidenceBundleSummaryMarkdownPathValue -Field "Bundle path" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Bundle path") -ExpectedPath $ResolvedBundlePath -SummaryDirectory (Split-Path -Path $summaryMarkdownPath -Parent)
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Bundle SHA-256" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Bundle SHA-256") -ExpectedValue $ExpectedBundleSha256.ToUpperInvariant()
    Assert-ReleaseEvidenceBundleSummaryMarkdownPathValue -Field "SHA-256 file" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "SHA-256 file") -ExpectedPath $ResolvedSha256Path -SummaryDirectory (Split-Path -Path $summaryMarkdownPath -Parent)
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Source files" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Source files") -ExpectedValue ([string]$expectedSourceFileCount)
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Failed prerequisite checks" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Failed prerequisite checks") -ExpectedValue ([string]$failedManifestChecks)
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Release check status" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Release check status") -ExpectedValue $expectedReleaseCheckStatus
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Release candidate commit" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Release candidate commit") -ExpectedValue $expectedReleaseCandidateCommit
    Assert-ReleaseEvidenceBundleSummaryMarkdownValue -Field "Release check allow dirty worktree" -ActualValue (Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field "Release check allow dirty worktree") -ExpectedValue $expectedAllowDirtyWorktree
}

function Format-ReleaseEvidenceBundleVerificationReportValue {
    param([object]$Value)

    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        return ""
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Save-ReleaseEvidenceBundleVerificationReport {
    param(
        [string]$ReportJsonPath,
        [string]$ReportMarkdownPath,
        [string]$Status,
        [string]$FailureReason,
        [string]$ResolvedBundlePath,
        [string]$ResolvedSha256Path
    )

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        bundlePath = $ResolvedBundlePath
        sha256Path = $ResolvedSha256Path
        status = $Status
        allowBlocked = $AllowBlocked.IsPresent
        failureCount = $verificationFailureCount
        failureReason = $FailureReason
        checks = @($verificationChecks)
    }

    $reportDirectory = Split-Path -Path $ReportJsonPath -Parent
    if ($reportDirectory -and -not (Test-Path -LiteralPath $reportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8

    $markdownLines = [System.Collections.Generic.List[string]]::new()
    $markdownLines.Add("# Release evidence bundle verification report")
    $markdownLines.Add("")
    $markdownLines.Add("| Field | Value |")
    $markdownLines.Add("| --- | --- |")
    $markdownLines.Add("| Status | $(Format-ReleaseEvidenceBundleVerificationReportValue $Status) |")
    $markdownLines.Add("| Bundle path | $(Format-ReleaseEvidenceBundleVerificationReportValue $ResolvedBundlePath) |")
    $markdownLines.Add("| SHA-256 file | $(Format-ReleaseEvidenceBundleVerificationReportValue $ResolvedSha256Path) |")
    $markdownLines.Add("| Failure count | $verificationFailureCount |")
    $markdownLines.Add("| Failure reason | $(Format-ReleaseEvidenceBundleVerificationReportValue $FailureReason) |")
    $markdownLines.Add("")
    $markdownLines.Add("| Check | Status | Detail |")
    $markdownLines.Add("| --- | --- | --- |")
    foreach ($check in @($verificationChecks)) {
        $markdownLines.Add("| $(Format-ReleaseEvidenceBundleVerificationReportValue $check.Name) | $(Format-ReleaseEvidenceBundleVerificationReportValue $check.Status) | $(Format-ReleaseEvidenceBundleVerificationReportValue $check.Detail) |")
    }
    $markdownLines | Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8

    [System.Console]::WriteLine("[release-evidence-bundle-verify] Verification report JSON: $ReportJsonPath")
    [System.Console]::WriteLine("[release-evidence-bundle-verify] Verification report Markdown: $ReportMarkdownPath")
}

$resolvedBundlePath = Get-ReleaseEvidenceBundlePath
$resolvedSha256Path = Get-ReleaseEvidenceBundleSha256Path -ResolvedBundlePath $resolvedBundlePath
$verificationReportJsonPath = "$resolvedBundlePath.verify-report.json"
$verificationReportMarkdownPath = "$resolvedBundlePath.verify-report.md"
$verificationStatus = "FAILED"
$verificationFailureReason = $null
$extractInfo = $null
try {
    $bundleSha256 = Assert-ReleaseEvidenceBundleSha256 -ResolvedBundlePath $resolvedBundlePath -ResolvedSha256Path $resolvedSha256Path
    if ($verificationFailureCount -gt 0) {
        throw "Release evidence bundle verification failed with $verificationFailureCount failed check(s) before archive extraction."
    }

    $extractInfo = New-ReleaseEvidenceBundleExtractDirectory
    try {
        Expand-ReleaseEvidenceBundle -ResolvedBundlePath $resolvedBundlePath -ExtractRoot $extractInfo.Path
        $manifest = Assert-ReleaseEvidenceBundleManifest -ExtractRoot $extractInfo.Path
        Assert-ReleaseEvidenceBundleSourceFiles -ExtractRoot $extractInfo.Path -Manifest $manifest
        Assert-ReleaseEvidenceBundleRequiredEvidence -ExtractRoot $extractInfo.Path -Manifest $manifest
        Assert-ReleaseEvidenceBundleCandidateCommit -ExtractRoot $extractInfo.Path
        Assert-ReleaseEvidenceBundleSummaryJson -ResolvedBundlePath $resolvedBundlePath -ExtractRoot $extractInfo.Path -Manifest $manifest -ExpectedBundleSha256 $bundleSha256
        Assert-ReleaseEvidenceBundleSummaryMarkdown -ResolvedBundlePath $resolvedBundlePath -ResolvedSha256Path $resolvedSha256Path -ExtractRoot $extractInfo.Path -Manifest $manifest -ExpectedBundleSha256 $bundleSha256
    }
    finally {
        if ($null -ne $extractInfo -and $extractInfo.Temporary -and (Test-Path -LiteralPath $extractInfo.Path -PathType Container)) {
            Remove-Item -LiteralPath $extractInfo.Path -Recurse -Force
        }
    }

    if ($verificationFailureCount -gt 0) {
        throw "Release evidence bundle verification failed with $verificationFailureCount failed check(s)."
    }

    $verificationStatus = "PASSED"
    [System.Console]::WriteLine("[release-evidence-bundle-verify] Release evidence bundle verification passed: $resolvedBundlePath")
}
catch {
    $verificationStatus = "FAILED"
    $verificationFailureReason = $_.Exception.Message
    throw
}
finally {
    Save-ReleaseEvidenceBundleVerificationReport -ReportJsonPath $verificationReportJsonPath -ReportMarkdownPath $verificationReportMarkdownPath -Status $verificationStatus -FailureReason $verificationFailureReason -ResolvedBundlePath $resolvedBundlePath -ResolvedSha256Path $resolvedSha256Path
}

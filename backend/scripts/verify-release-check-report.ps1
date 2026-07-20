param(
    [string]$ReportDirectory,
    [string]$JsonPath,
    [string]$MarkdownPath,
    [switch]$AllowFailed,
    [switch]$AllowDirtyWorktree
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sha256-helpers.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $RepoRoot "target"
}
if ([string]::IsNullOrWhiteSpace($JsonPath)) {
    $JsonPath = Join-Path $ReportDirectory "release-check-report.json"
}
if ([string]::IsNullOrWhiteSpace($MarkdownPath)) {
    $MarkdownPath = Join-Path $ReportDirectory "release-check-report.md"
}

$verificationChecks = [System.Collections.Generic.List[object]]::new()
$verificationFailureCount = 0

function Add-ReleaseCheckReportVerificationCheck {
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
    [System.Console]::WriteLine("[release-check-report-verify] $Status $Name - $Detail")
    if ($Status -eq "FAILED") {
        $script:verificationFailureCount++
    }
}

function Get-ReleaseCheckReportObjectProperty {
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

function Resolve-ReleaseCheckReportArtifactPath {
    param(
        [string]$RepositoryRoot,
        [string]$RelativePath
    )

    if ([string]::IsNullOrWhiteSpace($RepositoryRoot) -or [string]::IsNullOrWhiteSpace($RelativePath)) {
        return $null
    }

    $rootFull = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    $pathFull = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))
    if (-not $pathFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $null
    }

    return $pathFull
}

function Assert-ReleaseCheckReportRequiredText {
    param(
        [string]$Name,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        Add-ReleaseCheckReportVerificationCheck $Name "FAILED" "$Name is missing or blank."
        return
    }

    Add-ReleaseCheckReportVerificationCheck $Name "PASSED" "$Name is present."
}

function Get-ReleaseCheckReportCanonicalRoot {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    try {
        return [System.IO.Path]::GetFullPath($Path).TrimEnd("\", "/")
    }
    catch {
        return $null
    }
}

function Assert-ReleaseCheckReportRepository {
    param([object]$Report)

    $reportedRepository = [string](Get-ReleaseCheckReportObjectProperty -Object $Report -Name "repository")
    if ([string]::IsNullOrWhiteSpace($reportedRepository)) {
        Add-ReleaseCheckReportVerificationCheck "repository" "FAILED" "repository is missing or blank."
        return
    }

    $reportedRoot = Get-ReleaseCheckReportCanonicalRoot -Path $reportedRepository
    $currentRoot = Get-ReleaseCheckReportCanonicalRoot -Path $RepoRoot
    if ($null -eq $reportedRoot -or $null -eq $currentRoot) {
        Add-ReleaseCheckReportVerificationCheck "repository" "FAILED" "repository path cannot be resolved."
        return
    }

    if ($reportedRoot.Equals($currentRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-ReleaseCheckReportVerificationCheck "repository" "PASSED" "reported repository matches current repository root."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "repository" "FAILED" "reported repository '$reportedRoot' does not match current repository root '$currentRoot'."
    }
}

function Assert-ReleaseCheckReportCommitMatchesHead {
    param([object]$Report)

    $reportedCommit = [string](Get-ReleaseCheckReportObjectProperty -Object $Report -Name "releaseCandidateCommit")
    if ([string]::IsNullOrWhiteSpace($reportedCommit)) {
        Add-ReleaseCheckReportVerificationCheck "releaseCandidateCommit" "FAILED" "releaseCandidateCommit is missing or blank."
        return
    }

    Push-Location $RepoRoot
    try {
        $currentCommit = (& git rev-parse --short HEAD 2>$null)
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentCommit)) {
            Add-ReleaseCheckReportVerificationCheck "releaseCandidateCommit" "FAILED" "Unable to resolve current HEAD commit."
            return
        }

        $currentCommit = [string]$currentCommit
        if ($reportedCommit.Equals($currentCommit, [System.StringComparison]::OrdinalIgnoreCase)) {
            Add-ReleaseCheckReportVerificationCheck "releaseCandidateCommit" "PASSED" "releaseCandidateCommit matches current HEAD $currentCommit."
        }
        else {
            Add-ReleaseCheckReportVerificationCheck "releaseCandidateCommit" "FAILED" "releaseCandidateCommit $reportedCommit does not match current HEAD $currentCommit."
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-ReleaseCheckReportDirtyWorktreePolicy {
    param(
        [object]$Report,
        [string]$Status
    )

    $reportAllowsDirtyWorktree = [bool](Get-ReleaseCheckReportObjectProperty -Object $Report -Name "allowDirtyWorktree")
    if ($Status -eq "PASSED" -and $reportAllowsDirtyWorktree -and -not $AllowDirtyWorktree) {
        Add-ReleaseCheckReportVerificationCheck "allowDirtyWorktree" "FAILED" "PASSED report was generated with -AllowDirtyWorktree; use -AllowDirtyWorktree only for local non-release investigation reports."
        return
    }

    Add-ReleaseCheckReportVerificationCheck "allowDirtyWorktree" "PASSED" "allowDirtyWorktree policy accepted."
}

function Assert-ReleaseCheckReportJson {
    if (-not (Test-Path -LiteralPath $JsonPath -PathType Leaf)) {
        Add-ReleaseCheckReportVerificationCheck "release-check-report.json" "FAILED" "JSON report does not exist: $JsonPath"
        return $null
    }

    try {
        $report = Get-Content -LiteralPath $JsonPath -Raw | ConvertFrom-Json
        Add-ReleaseCheckReportVerificationCheck "release-check-report.json" "PASSED" "Parsed JSON report."
        return $report
    }
    catch {
        Add-ReleaseCheckReportVerificationCheck "release-check-report.json" "FAILED" "Cannot parse JSON report: $($_.Exception.Message)"
        return $null
    }
}

function Assert-ReleaseCheckReportMarkdown {
    param([object]$Report)

    if (-not (Test-Path -LiteralPath $MarkdownPath -PathType Leaf)) {
        Add-ReleaseCheckReportVerificationCheck "release-check-report.md" "FAILED" "Markdown report does not exist: $MarkdownPath"
        return
    }

    $markdown = Get-Content -LiteralPath $MarkdownPath -Raw
    Add-ReleaseCheckReportVerificationCheck "release-check-report.md" "PASSED" "Found Markdown report."
    if ($markdown.Contains("# Release Check Report")) {
        Add-ReleaseCheckReportVerificationCheck "Markdown title" "PASSED" "Markdown title is present."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "Markdown title" "FAILED" "Markdown title is missing."
    }

    $status = [string](Get-ReleaseCheckReportObjectProperty -Object $Report -Name "status")
    if (-not [string]::IsNullOrWhiteSpace($status) -and $markdown.Contains("Status: $status")) {
        Add-ReleaseCheckReportVerificationCheck "Markdown status" "PASSED" "Markdown contains status $status."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "Markdown status" "FAILED" "Markdown does not contain the report status."
    }
}

function Assert-ReleaseCheckReportEnvironment {
    param([object]$Environment)

    if ($null -eq $Environment) {
        Add-ReleaseCheckReportVerificationCheck "environment" "FAILED" "environment is missing."
        return
    }

    Add-ReleaseCheckReportVerificationCheck "environment" "PASSED" "environment is present."
    Assert-ReleaseCheckReportRequiredText "environment.operatingSystem" ([string](Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "operatingSystem"))

    $powerShell = Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "powerShell"
    Assert-ReleaseCheckReportRequiredText "environment.powerShell.version" ([string](Get-ReleaseCheckReportObjectProperty -Object $powerShell -Name "version"))
    Assert-ReleaseCheckReportRequiredText "environment.javaVersion" ([string](Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "javaVersion"))
    Assert-ReleaseCheckReportRequiredText "environment.mavenWrapperPath" ([string](Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "mavenWrapperPath"))
    Assert-ReleaseCheckReportRequiredText "environment.mavenVersion" ([string](Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "mavenVersion"))
    Assert-ReleaseCheckReportRequiredText "environment.dockerVersion" ([string](Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "dockerVersion"))

    $ci = Get-ReleaseCheckReportObjectProperty -Object $Environment -Name "ci"
    if ($null -ne $ci) {
        Add-ReleaseCheckReportVerificationCheck "environment.ci" "PASSED" "CI metadata object is present."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "environment.ci" "FAILED" "CI metadata object is missing."
    }
}

function Assert-ReleaseCheckReportArtifacts {
    param(
        [string]$RepositoryRoot,
        [object[]]$Artifacts,
        [string]$Status
    )

    $artifactList = @($Artifacts)
    if ($Status -eq "PASSED" -and $artifactList.Count -eq 0) {
        Add-ReleaseCheckReportVerificationCheck "artifacts" "FAILED" "PASSED report must list at least one artifact."
        return
    }
    if ($artifactList.Count -eq 0) {
        Add-ReleaseCheckReportVerificationCheck "artifacts" "PASSED" "No artifacts listed for non-PASSED report."
        return
    }

    foreach ($artifact in $artifactList) {
        $relativePath = [string](Get-ReleaseCheckReportObjectProperty -Object $artifact -Name "relativePath")
        Assert-ReleaseCheckReportRequiredText "artifact.relativePath" $relativePath
        $artifactPath = Resolve-ReleaseCheckReportArtifactPath -RepositoryRoot $RepositoryRoot -RelativePath $relativePath
        if ([string]::IsNullOrWhiteSpace($artifactPath)) {
            Add-ReleaseCheckReportVerificationCheck "Artifact path $relativePath" "FAILED" "Artifact path is missing or escapes repository root."
            continue
        }
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            Add-ReleaseCheckReportVerificationCheck "Artifact file $relativePath" "FAILED" "Artifact file does not exist."
            continue
        }

        $fileInfo = Get-Item -LiteralPath $artifactPath
        $expectedLength = [long](Get-ReleaseCheckReportObjectProperty -Object $artifact -Name "length")
        if ($fileInfo.Length -eq $expectedLength) {
            Add-ReleaseCheckReportVerificationCheck "Artifact length $relativePath" "PASSED" "Length matches $expectedLength."
        }
        else {
            Add-ReleaseCheckReportVerificationCheck "Artifact length $relativePath" "FAILED" "Expected $expectedLength byte(s) but found $($fileInfo.Length)."
        }

        $expectedHash = ([string](Get-ReleaseCheckReportObjectProperty -Object $artifact -Name "sha256")).ToUpperInvariant()
        if (-not ($expectedHash -match "^[A-F0-9]{64}$")) {
            Add-ReleaseCheckReportVerificationCheck "Artifact SHA-256 $relativePath" "FAILED" "Artifact SHA-256 is missing or invalid."
            continue
        }

        $actualHash = (Get-Sha256Hex -LiteralPath $artifactPath).ToUpperInvariant()
        if ($actualHash -eq $expectedHash) {
            Add-ReleaseCheckReportVerificationCheck "Artifact SHA-256 $relativePath" "PASSED" "SHA-256 matches report."
        }
        else {
            Add-ReleaseCheckReportVerificationCheck "Artifact SHA-256 $relativePath" "FAILED" "Expected $expectedHash but found $actualHash."
        }
    }
}

$resolvedJsonPath = [System.IO.Path]::GetFullPath($JsonPath)
$report = Assert-ReleaseCheckReportJson
if ($null -ne $report) {
    Assert-ReleaseCheckReportMarkdown -Report $report

    $schemaVersion = [int](Get-ReleaseCheckReportObjectProperty -Object $report -Name "schemaVersion")
    if ($schemaVersion -eq 1) {
        Add-ReleaseCheckReportVerificationCheck "schemaVersion" "PASSED" "schemaVersion is 1."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "schemaVersion" "FAILED" "schemaVersion is $schemaVersion, expected 1."
    }

    Assert-ReleaseCheckReportRequiredText "releaseCandidateCommit" ([string](Get-ReleaseCheckReportObjectProperty -Object $report -Name "releaseCandidateCommit"))
    Assert-ReleaseCheckReportCommitMatchesHead -Report $report
    Assert-ReleaseCheckReportRepository -Report $report
    $status = ([string](Get-ReleaseCheckReportObjectProperty -Object $report -Name "status")).ToUpperInvariant()
    Assert-ReleaseCheckReportDirtyWorktreePolicy -Report $report -Status $status
    if ($status -eq "PASSED") {
        Add-ReleaseCheckReportVerificationCheck "status" "PASSED" "status is PASSED."
    }
    elseif ($status -eq "FAILED" -and $AllowFailed) {
        Add-ReleaseCheckReportVerificationCheck "status" "PASSED" "status is FAILED and -AllowFailed was specified."
        Assert-ReleaseCheckReportRequiredText "failureReason" ([string](Get-ReleaseCheckReportObjectProperty -Object $report -Name "failureReason"))
    }
    elseif ($status -eq "FAILED") {
        Add-ReleaseCheckReportVerificationCheck "status" "FAILED" "status is FAILED; use -AllowFailed only for failed release-check investigation reports."
        Assert-ReleaseCheckReportRequiredText "failureReason" ([string](Get-ReleaseCheckReportObjectProperty -Object $report -Name "failureReason"))
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "status" "FAILED" "status is '$status', expected PASSED or FAILED."
    }

    $syntaxGate = Get-ReleaseCheckReportObjectProperty -Object $report -Name "powerShellScriptSyntaxGate"
    $syntaxStatus = ([string](Get-ReleaseCheckReportObjectProperty -Object $syntaxGate -Name "status")).ToUpperInvariant()
    if ($status -eq "PASSED" -and $syntaxStatus -ne "PASSED") {
        Add-ReleaseCheckReportVerificationCheck "powerShellScriptSyntaxGate" "FAILED" "PASSED report requires PowerShell syntax gate PASSED."
    }
    elseif ([string]::IsNullOrWhiteSpace($syntaxStatus)) {
        Add-ReleaseCheckReportVerificationCheck "powerShellScriptSyntaxGate" "FAILED" "PowerShell syntax gate status is missing."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "powerShellScriptSyntaxGate" "PASSED" "PowerShell syntax gate status is $syntaxStatus."
    }

    $maven = Get-ReleaseCheckReportObjectProperty -Object $report -Name "maven"
    $mavenStatus = ([string](Get-ReleaseCheckReportObjectProperty -Object $maven -Name "status")).ToUpperInvariant()
    if ($status -eq "PASSED" -and $mavenStatus -eq "PASSED") {
        Add-ReleaseCheckReportVerificationCheck "maven.status" "PASSED" "Maven status is PASSED."
    }
    elseif ($status -eq "PASSED") {
        Add-ReleaseCheckReportVerificationCheck "maven.status" "FAILED" "PASSED report requires Maven status PASSED."
    }
    elseif (-not [string]::IsNullOrWhiteSpace($mavenStatus)) {
        Add-ReleaseCheckReportVerificationCheck "maven.status" "PASSED" "Maven status is $mavenStatus for failed report."
    }
    else {
        Add-ReleaseCheckReportVerificationCheck "maven.status" "FAILED" "Maven status is missing."
    }

    Assert-ReleaseCheckReportEnvironment -Environment (Get-ReleaseCheckReportObjectProperty -Object $report -Name "environment")
    Assert-ReleaseCheckReportArtifacts `
        -RepositoryRoot $RepoRoot `
        -Artifacts @((Get-ReleaseCheckReportObjectProperty -Object $report -Name "artifacts")) `
        -Status $status
}

if ($verificationFailureCount -gt 0) {
    throw "Release check report verification failed with $verificationFailureCount failed check(s)."
}

[System.Console]::WriteLine("[release-check-report-verify] Release check report verification passed: $resolvedJsonPath")

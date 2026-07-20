param(
    [string]$MavenRepoLocal,
    [string]$ReportDirectory,
    [switch]$AllowDirtyWorktree,
    [switch]$IncludeTestcontainers
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "sha256-helpers.ps1")

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $MavenRepoLocal) {
    $MavenRepoLocal = Join-Path $RepoRoot ".m2\repository"
}
if (-not $ReportDirectory) {
    $ReportDirectory = Join-Path $RepoRoot "target"
}

function Get-ReleaseRelativePath {
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

function Get-ReleasePowerShellScriptPaths {
    param([string]$RepositoryRoot)

    $scriptsRoot = Join-Path $RepositoryRoot "scripts"
    if (-not (Test-Path -LiteralPath $scriptsRoot -PathType Container)) {
        throw "Missing scripts directory: $scriptsRoot"
    }

    $scriptFiles = @(Get-ChildItem -LiteralPath $scriptsRoot -Filter "*.ps1" -File -Recurse | Sort-Object FullName)
    if ($scriptFiles.Count -eq 0) {
        throw "No PowerShell scripts were found under $scriptsRoot."
    }

    return @($scriptFiles | ForEach-Object { Get-ReleaseRelativePath -Root $RepositoryRoot -Path $_.FullName })
}

function Get-ReleaseMavenWrapperPath {
    param([string]$RepositoryRoot)

    $isWindowsHost = [System.IO.Path]::DirectorySeparatorChar -eq "\"
    $preferredWrapper = if ($isWindowsHost) { "mvnw.cmd" } else { "mvnw" }
    $fallbackWrapper = if ($isWindowsHost) { "mvnw" } else { "mvnw.cmd" }

    foreach ($wrapperName in @($preferredWrapper, $fallbackWrapper)) {
        $candidate = Join-Path $RepositoryRoot $wrapperName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    throw "Missing Maven wrapper under $RepositoryRoot; expected mvnw or mvnw.cmd."
}

function Assert-ReleaseMavenRepoLocalWritable {
    param([string]$MavenRepoLocal)

    $repoPath = [System.IO.Path]::GetFullPath($MavenRepoLocal)
    try {
        if (Test-Path -LiteralPath $repoPath -PathType Leaf) {
            throw "Path points to a file, not a directory."
        }
        if (-not (Test-Path -LiteralPath $repoPath -PathType Container)) {
            New-Item -ItemType Directory -Path $repoPath -Force | Out-Null
        }

        $probeName = ".release-check-write-probe-$([System.Guid]::NewGuid().ToString('N'))"
        $probePath = Join-Path $repoPath $probeName
        try {
            [System.IO.File]::WriteAllText($probePath, "release-check", [System.Text.Encoding]::UTF8)
        }
        finally {
            Remove-Item -LiteralPath $probePath -Force -ErrorAction SilentlyContinue
        }
    }
    catch {
        throw "Maven local repository is not writable: $repoPath. Pass -MavenRepoLocal with a writable directory or fix repository permissions. $($_.Exception.Message)"
    }
}

function Invoke-ReleaseCheckVersionCommand {
    param(
        [string]$CommandText,
        [scriptblock]$Command
    )

    try {
        $output = @(& $Command 2>&1)
        $exitCode = $LASTEXITCODE
        $text = ($output | ForEach-Object { [string]$_ }) -join "`n"
        if ([string]::IsNullOrWhiteSpace($text)) {
            $text = "<no output>"
        }
        if ($null -ne $exitCode -and $exitCode -ne 0) {
            return "UNAVAILABLE: $CommandText exited with code $exitCode. $text"
        }

        return $text.Trim()
    }
    catch {
        return "UNAVAILABLE: $CommandText failed. $($_.Exception.Message)"
    }
}

function Get-ReleaseCheckEnvironment {
    param(
        [string]$RepositoryRoot,
        [string]$MavenWrapperPath
    )

    $mavenWrapperRelativePath = $null
    $mavenVersion = "UNAVAILABLE: Maven wrapper was not resolved."
    if (-not [string]::IsNullOrWhiteSpace($MavenWrapperPath)) {
        $mavenWrapperRelativePath = Get-ReleaseRelativePath -Root $RepositoryRoot -Path $MavenWrapperPath
        $mavenVersion = Invoke-ReleaseCheckVersionCommand -CommandText "$mavenWrapperRelativePath --version" -Command {
            & $MavenWrapperPath --version
        }
    }

    $dockerVersion = "UNAVAILABLE: docker command was not found."
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $dockerVersion = Invoke-ReleaseCheckVersionCommand -CommandText "docker --version" -Command {
            & docker --version
        }
    }

    return [pscustomobject]([ordered]@{
        operatingSystem = [System.Environment]::OSVersion.VersionString
        is64BitOperatingSystem = [System.Environment]::Is64BitOperatingSystem
        powerShell = [pscustomobject]([ordered]@{
            version = $PSVersionTable.PSVersion.ToString()
            edition = [string]$PSVersionTable.PSEdition
        })
        javaVersion = Invoke-ReleaseCheckVersionCommand -CommandText "java -version" -Command {
            & java -version
        }
        mavenWrapperPath = $mavenWrapperRelativePath
        mavenVersion = $mavenVersion
        dockerVersion = $dockerVersion
        ci = [pscustomobject]([ordered]@{
            githubActions = [string]$env:GITHUB_ACTIONS
            githubRunId = [string]$env:GITHUB_RUN_ID
            githubSha = [string]$env:GITHUB_SHA
            runnerOS = [string]$env:RUNNER_OS
        })
    })
}

function Assert-ReleasePowerShellScriptsParse {
    param(
        [string]$RepositoryRoot,
        [string[]]$ScriptPaths
    )

    [System.Console]::WriteLine("PowerShell script syntax gate: parsing $($ScriptPaths.Count) script(s)")
    $failed = $false
    foreach ($relativePath in $ScriptPaths) {
        $scriptPath = Join-Path $RepositoryRoot $relativePath
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
            [System.Console]::WriteLine("[script-syntax] FAILED $relativePath - missing file")
            $failed = $true
            continue
        }

        $tokens = $null
        $errors = $null
        [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $scriptPath), [ref] $tokens, [ref] $errors) | Out-Null
        if ($errors.Count -gt 0) {
            [System.Console]::WriteLine("[script-syntax] FAILED $relativePath")
            foreach ($error in $errors) {
                [System.Console]::WriteLine(("  {0}:{1} {2}" -f $error.Extent.StartLineNumber, $error.Extent.StartColumnNumber, $error.Message))
            }
            $failed = $true
        }
    }

    if ($failed) {
        throw "PowerShell script syntax gate failed."
    }

    [System.Console]::WriteLine("Release PowerShell script syntax gate passed.")
    return [pscustomobject]@{
        status = "PASSED"
        scriptCount = $ScriptPaths.Count
        scripts = @($ScriptPaths)
    }
}

function Save-ReleaseCheckReport {
    param(
        [string]$RepositoryRoot,
        [string]$ReportDirectory,
        [ValidateSet("PASSED", "FAILED")]
        [string]$Status = "PASSED",
        [string]$FailureReason,
        [string]$Commit,
        [string[]]$WorktreeStatus,
        [string[]]$MavenArgs,
        [object]$PowerShellScriptSyntaxGate,
        [object[]]$Artifacts,
        [object]$Environment,
        [switch]$AllowDirtyWorktree,
        [switch]$IncludeTestcontainers
    )

    if (-not (Test-Path -LiteralPath $ReportDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
    }

    $manualAcceptanceChecklist = "docs\business-readiness-checklist.md"
    $artifactList = @($Artifacts)
    $mavenStatus = if ($Status -eq "PASSED") {
        "PASSED"
    }
    elseif (@($MavenArgs).Count -gt 0) {
        "FAILED"
    }
    else {
        "NOT_RUN"
    }
    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = Get-Date -Format "o"
        repository = $RepositoryRoot
        releaseCandidateCommit = $Commit
        status = $Status
        failureReason = $FailureReason
        allowDirtyWorktree = $AllowDirtyWorktree.IsPresent
        includeTestcontainers = $IncludeTestcontainers.IsPresent
        dirtyWorktreeEntries = @($WorktreeStatus)
        powerShellScriptSyntaxGate = $PowerShellScriptSyntaxGate
        maven = [ordered]@{
            status = $mavenStatus
            args = @($MavenArgs)
        }
        artifacts = $artifactList
        environment = $Environment
        manualAcceptanceChecklist = $manualAcceptanceChecklist
    }

    $jsonPath = Join-Path $ReportDirectory "release-check-report.json"
    $markdownPath = Join-Path $ReportDirectory "release-check-report.md"
    $report | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

    $markdown = [System.Collections.Generic.List[string]]::new()
    $markdown.Add("# Release Check Report")
    $markdown.Add("")
    $markdown.Add("- Status: $Status")
    if ($Status -eq "FAILED") {
        $markdown.Add("- Failure reason: $FailureReason")
    }
    $markdown.Add("- Release candidate commit: $Commit")
    $markdown.Add("- Generated at: $($report.generatedAt)")
    $markdown.Add("- Allow dirty worktree: $($AllowDirtyWorktree.IsPresent)")
    $markdown.Add("- Include Testcontainers: $($IncludeTestcontainers.IsPresent)")
    $markdown.Add(('- Manual acceptance checklist: `{0}`' -f $manualAcceptanceChecklist))
    $markdown.Add("")
    $markdown.Add("## PowerShell script syntax gate")
    $markdown.Add("")
    $markdown.Add("- Status: $($PowerShellScriptSyntaxGate.status)")
    $markdown.Add("- Script count: $($PowerShellScriptSyntaxGate.scriptCount)")
    $markdown.Add("")
    $markdown.Add("## Dirty worktree")
    $markdown.Add("")
    if ($WorktreeStatus.Count -eq 0) {
        $markdown.Add('- No uncommitted changes were reported by `git status --short`.')
    }
    else {
        foreach ($line in $WorktreeStatus) {
            $markdown.Add(('- `{0}`' -f $line))
        }
    }
    $markdown.Add("")
    $markdown.Add("## Maven")
    $markdown.Add("")
    $markdown.Add("- Status: $mavenStatus")
    if (@($MavenArgs).Count -gt 0) {
        $markdown.Add(('- Args: `{0}`' -f ($MavenArgs -join ' ')))
    }
    else {
        $markdown.Add("- Args: <not assembled>")
    }
    $markdown.Add("")
    $markdown.Add("## Artifacts")
    $markdown.Add("")
    if ($artifactList.Count -gt 0) {
        $markdown.Add("| Path | Bytes | SHA-256 |")
        $markdown.Add("| --- | ---: | --- |")
        foreach ($artifact in $artifactList) {
            $markdown.Add(('| `{0}` | {1} | `{2}` |' -f $artifact.relativePath, $artifact.length, $artifact.sha256))
        }
    }
    else {
        $markdown.Add("- No release artifacts were verified.")
    }
    $markdown.Add("")
    $markdown.Add("## Environment")
    $markdown.Add("")
    if ($null -eq $Environment) {
        $markdown.Add("- Environment fingerprint was not collected.")
    }
    else {
        $markdown.Add(('- OS: `{0}`' -f $Environment.operatingSystem))
        $markdown.Add(('- 64-bit OS: `{0}`' -f $Environment.is64BitOperatingSystem))
        $markdown.Add(('- PowerShell: `{0}` `{1}`' -f $Environment.powerShell.version, $Environment.powerShell.edition))
        $markdown.Add(('- Java: `{0}`' -f (($Environment.javaVersion -split "`n") | Select-Object -First 1)))
        $markdown.Add(('- Maven wrapper: `{0}`' -f $Environment.mavenWrapperPath))
        $markdown.Add(('- Maven: `{0}`' -f (($Environment.mavenVersion -split "`n") | Select-Object -First 1)))
        $markdown.Add(('- Docker: `{0}`' -f (($Environment.dockerVersion -split "`n") | Select-Object -First 1)))
        $markdown.Add(('- GitHub Actions: `{0}`' -f $Environment.ci.githubActions))
        $markdown.Add(('- GitHub run id: `{0}`' -f $Environment.ci.githubRunId))
        $markdown.Add(('- GitHub SHA: `{0}`' -f $Environment.ci.githubSha))
        $markdown.Add(('- Runner OS: `{0}`' -f $Environment.ci.runnerOS))
    }

    $markdown | Set-Content -LiteralPath $markdownPath -Encoding UTF8

    [System.Console]::WriteLine("Release check report JSON: $(Get-ReleaseRelativePath -Root $RepositoryRoot -Path $jsonPath)")
    [System.Console]::WriteLine("Release check report Markdown: $(Get-ReleaseRelativePath -Root $RepositoryRoot -Path $markdownPath)")
}

function Invoke-ReleaseCheckReportVerifier {
    param(
        [string]$RepositoryRoot,
        [string]$ReportDirectory,
        [switch]$AllowFailed,
        [switch]$AllowDirtyWorktree
    )

    $verifierPath = Join-Path $RepositoryRoot "scripts\verify-release-check-report.ps1"
    if (-not (Test-Path -LiteralPath $verifierPath -PathType Leaf)) {
        throw "Missing release check report verifier: $verifierPath"
    }

    $verifierParams = @{ ReportDirectory = $ReportDirectory }
    $verifierDisplayArgs = @("-ReportDirectory", $ReportDirectory)
    if ($AllowFailed) {
        $verifierParams.AllowFailed = $true
        $verifierDisplayArgs += "-AllowFailed"
    }
    if ($AllowDirtyWorktree) {
        $verifierParams.AllowDirtyWorktree = $true
        $verifierDisplayArgs += "-AllowDirtyWorktree"
    }

    $verifierRelativePath = Get-ReleaseRelativePath -Root $RepositoryRoot -Path $verifierPath
    [System.Console]::WriteLine("Release check report self-verification: $verifierRelativePath $($verifierDisplayArgs -join ' ')")
    & $verifierPath @verifierParams
}

$commit = $null
$worktreeStatus = @()
$mavenArgs = @()
$powerShellScriptSyntaxGate = [pscustomobject]@{
    status = "NOT_RUN"
    scriptCount = 0
    scripts = @()
}
$verifiedArtifacts = @()
$mavenWrapper = $null
$releaseEnvironment = [pscustomobject]([ordered]@{
    status = "NOT_COLLECTED"
})

Push-Location $RepoRoot
try {
    $mavenWrapper = Get-ReleaseMavenWrapperPath -RepositoryRoot $RepoRoot
    $releaseEnvironment = Get-ReleaseCheckEnvironment -RepositoryRoot $RepoRoot -MavenWrapperPath $mavenWrapper

    $commit = (& git rev-parse --short HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $commit) {
        throw "Unable to resolve release candidate commit"
    }
    [System.Console]::WriteLine("Release candidate commit: $commit")

    $worktreeStatus = @(& git status --short 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect git worktree status"
    }
    if ($worktreeStatus.Count -gt 0) {
        [System.Console]::WriteLine("Working tree has uncommitted changes:")
        foreach ($line in $worktreeStatus) {
            [System.Console]::WriteLine("  $line")
        }
        if (-not $AllowDirtyWorktree) {
            throw "Working tree has uncommitted changes. Commit or stash them, or rerun with -AllowDirtyWorktree for a local non-release check."
        }
        [System.Console]::WriteLine("Continuing because -AllowDirtyWorktree was specified.")
    }

    $powerShellScriptSyntaxGate = Assert-ReleasePowerShellScriptsParse -RepositoryRoot $RepoRoot -ScriptPaths (Get-ReleasePowerShellScriptPaths -RepositoryRoot $RepoRoot)
    Assert-ReleaseMavenRepoLocalWritable -MavenRepoLocal $MavenRepoLocal

    $mavenArgs = @("-Dmaven.repo.local=$MavenRepoLocal")
    if ($IncludeTestcontainers) {
        [System.Console]::WriteLine("Including Testcontainers integration tests in release gate")
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            throw "Docker is required when -IncludeTestcontainers is specified"
        }
        & docker --version
        if ($LASTEXITCODE -ne 0) {
            throw "Docker is required when -IncludeTestcontainers is specified"
        }
        $mavenArgs += "-Ptestcontainers"
        $mavenArgs += "-Derp.testcontainers.enabled=true"
    }
    $mavenArgs += "clean"
    $mavenArgs += "package"

    [System.Console]::WriteLine("Running release gate: Maven $($mavenArgs -join ' ')")
    & $mavenWrapper @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven clean package failed with exit code $LASTEXITCODE"
    }

    $requiredArtifacts = @(
        "target\erp-server-1.0.0.jar",
        "target\classes\META-INF\sbom\application.cdx.json",
        "target\bom.json"
    )

    $verifiedArtifacts = @()
    foreach ($relativePath in $requiredArtifacts) {
        $artifactPath = Join-Path $RepoRoot $relativePath
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            throw "Missing required release artifact: $relativePath"
        }
        $artifactInfo = Get-Item -LiteralPath $artifactPath
        $artifactHash = Get-Sha256Hex -LiteralPath $artifactPath
        $verifiedArtifacts += [pscustomobject]@{
            relativePath = $relativePath
            length = $artifactInfo.Length
            lastWriteTimeUtc = $artifactInfo.LastWriteTimeUtc.ToString("o")
            sha256 = $artifactHash
        }
        [System.Console]::WriteLine("Verified artifact: $relativePath")
    }

    Save-ReleaseCheckReport `
        -RepositoryRoot $RepoRoot `
        -ReportDirectory $ReportDirectory `
        -Status "PASSED" `
        -Commit $commit `
        -WorktreeStatus $worktreeStatus `
        -MavenArgs $mavenArgs `
        -PowerShellScriptSyntaxGate $powerShellScriptSyntaxGate `
        -Artifacts $verifiedArtifacts `
        -Environment $releaseEnvironment `
        -AllowDirtyWorktree:$AllowDirtyWorktree `
        -IncludeTestcontainers:$IncludeTestcontainers

    Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory -AllowDirtyWorktree:$AllowDirtyWorktree

    [System.Console]::WriteLine("Release gate passed.")
    [System.Console]::WriteLine("Manual acceptance checklist: docs\business-readiness-checklist.md")
}
catch {
    $releaseCheckFailure = $_
    $failureReason = $releaseCheckFailure.Exception.Message
    [System.Console]::WriteLine("Release gate failed: $failureReason")
    try {
        Save-ReleaseCheckReport `
            -RepositoryRoot $RepoRoot `
            -ReportDirectory $ReportDirectory `
            -Status "FAILED" `
            -FailureReason $failureReason `
            -Commit $commit `
            -WorktreeStatus $worktreeStatus `
            -MavenArgs $mavenArgs `
            -PowerShellScriptSyntaxGate $powerShellScriptSyntaxGate `
            -Artifacts $verifiedArtifacts `
            -Environment $releaseEnvironment `
            -AllowDirtyWorktree:$AllowDirtyWorktree `
            -IncludeTestcontainers:$IncludeTestcontainers
        Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory -AllowFailed -AllowDirtyWorktree:$AllowDirtyWorktree
    }
    catch {
        [System.Console]::WriteLine("Unable to write or verify failed release check report: $($_.Exception.Message)")
    }
    throw $releaseCheckFailure
}
finally {
    Pop-Location
}

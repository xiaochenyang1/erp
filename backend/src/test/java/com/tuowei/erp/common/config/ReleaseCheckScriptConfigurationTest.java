package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseCheckScriptConfigurationTest {

    @Test
    void releaseCheckRejectsDirtyWorktreeUnlessExplicitlyAllowed() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[switch]$AllowDirtyWorktree")
                .contains("git status --short")
                .contains("git rev-parse --short HEAD")
                .contains("Release candidate commit:")
                .contains("Working tree has uncommitted changes")
                .contains("-AllowDirtyWorktree");
    }

    @Test
    void releaseCheckCanIncludeTestcontainersGateWhenDockerIsAvailable() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[switch]$IncludeTestcontainers")
                .contains("Get-Command docker")
                .contains("docker --version")
                .contains("-Ptestcontainers")
                .contains("-Derp.testcontainers.enabled=true")
                .contains("Including Testcontainers integration tests in release gate");
    }

    @Test
    void releaseCheckSelectsMavenWrapperForCurrentOperatingSystem() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Get-ReleaseMavenWrapperPath")
                .contains("[System.IO.Path]::DirectorySeparatorChar")
                .contains("mvnw.cmd")
                .contains("mvnw")
                .contains("$mavenWrapper = Get-ReleaseMavenWrapperPath -RepositoryRoot $RepoRoot")
                .contains("& $mavenWrapper @mavenArgs");
    }

    @Test
    void releaseCheckUsesPlatformNeutralPathsForVerifierAndArtifacts() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("Join-Path $RepositoryRoot \"scripts/verify-release-check-report.ps1\"")
                .contains("\"target/erp-server-1.0.0.jar\"")
                .contains("\"target/classes/META-INF/sbom/application.cdx.json\"")
                .contains("\"target/bom.json\"")
                .doesNotContain("Join-Path $RepositoryRoot \"scripts\\verify-release-check-report.ps1\"")
                .doesNotContain("\"target\\erp-server-1.0.0.jar\"");
    }

    @Test
    void releaseCheckParsesReleasePowerShellScriptsBeforeMavenGate() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Get-ReleasePowerShellScriptPaths")
                .contains("Get-ChildItem")
                .contains("-Filter \"*.ps1\"")
                .contains("-Recurse")
                .contains("Sort-Object")
                .contains("function Assert-ReleasePowerShellScriptsParse")
                .contains("[System.Management.Automation.Language.Parser]::ParseFile")
                .contains("PowerShell script syntax gate")
                .contains("Release PowerShell script syntax gate passed")
                .contains("Assert-ReleasePowerShellScriptsParse -RepositoryRoot $RepoRoot -ScriptPaths (Get-ReleasePowerShellScriptPaths -RepositoryRoot $RepoRoot)")
                .contains("Running release gate: Maven");
        assertThat(script.indexOf("Assert-ReleasePowerShellScriptsParse"))
                .isLessThan(script.indexOf("Running release gate: Maven"));
        assertThat(checklist)
                .contains("PowerShell 发布脚本语法")
                .contains("自动发现");
    }

    @Test
    void releaseCheckFailsFastWhenMavenLocalRepositoryIsNotWritable() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Assert-ReleaseMavenRepoLocalWritable")
                .contains("Maven local repository is not writable")
                .contains("Pass -MavenRepoLocal with a writable directory")
                .contains("Assert-ReleaseMavenRepoLocalWritable -MavenRepoLocal $MavenRepoLocal")
                .contains("Running release gate: Maven");
        assertThat(script.indexOf("Assert-ReleaseMavenRepoLocalWritable -MavenRepoLocal $MavenRepoLocal"))
                .isLessThan(script.indexOf("Running release gate: Maven"));
    }

    @Test
    void releaseCheckWritesStructuredReleaseGateReports() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[string]$ReportDirectory")
                .contains("function Save-ReleaseCheckReport")
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("sha256-helpers.ps1")
                .contains("Get-Sha256Hex")
                .containsIgnoringCase("sha256")
                .contains("powerShellScriptSyntaxGate")
                .contains("dirtyWorktreeEntries")
                .contains("releaseCandidateCommit")
                .contains("artifacts")
                .contains("manualAcceptanceChecklist");
        assertThat(checklist)
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("发布门禁报告");
    }

    @Test
    void releaseCheckWritesEnvironmentFingerprintToReports() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Invoke-ReleaseCheckVersionCommand")
                .contains("function Get-ReleaseCheckEnvironment")
                .contains("[System.Environment]::OSVersion")
                .contains("$PSVersionTable.PSVersion")
                .contains("java -version")
                .contains("--version")
                .contains("docker --version")
                .contains("GITHUB_ACTIONS")
                .contains("GITHUB_RUN_ID")
                .contains("RUNNER_OS")
                .contains("environment = $Environment")
                .contains("## Environment");
    }

    @Test
    void releaseCheckWritesFailureReportBeforeRethrowing() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[ValidateSet(\"PASSED\", \"FAILED\")]")
                .contains("[string]$Status")
                .contains("[string]$FailureReason")
                .contains("failureReason")
                .contains("Release gate failed")
                .contains("catch")
                .contains("-Status \"FAILED\"")
                .contains("-FailureReason");
    }

    @Test
    void releaseCheckSelfVerifiesGeneratedReportsBeforeCompleting() throws IOException {
        String script = Files.readString(Path.of("scripts", "release-check.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Invoke-ReleaseCheckReportVerifier")
                .contains("verify-release-check-report.ps1")
                .contains("-ReportDirectory")
                .contains("-AllowFailed")
                .contains("-AllowDirtyWorktree")
                .contains("$verifierParams = @{ ReportDirectory = $ReportDirectory }")
                .contains("$verifierParams.AllowDirtyWorktree = $true")
                .contains("& $verifierPath @verifierParams")
                .contains("Release check report self-verification")
                .contains("Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory")
                .contains("Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory -AllowFailed")
                .doesNotContain("& $verifierPath @verifierArgs");

        int passedReportWrite = script.indexOf("-Status \"PASSED\"");
        int passedReportVerify = script.indexOf("Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory");
        int releaseGatePassed = script.indexOf("Release gate passed.");
        assertThat(passedReportWrite).isLessThan(passedReportVerify);
        assertThat(passedReportVerify).isLessThan(releaseGatePassed);

        int failedReportWrite = script.indexOf("-Status \"FAILED\"");
        int failedReportVerify = script.indexOf("Invoke-ReleaseCheckReportVerifier -RepositoryRoot $RepoRoot -ReportDirectory $ReportDirectory -AllowFailed");
        int rethrowFailure = script.lastIndexOf("throw $releaseCheckFailure");
        assertThat(failedReportWrite).isLessThan(failedReportVerify);
        assertThat(failedReportVerify).isLessThan(rethrowFailure);
    }

    @Test
    void releaseCheckReportVerifierValidatesSchemaHashesAndFailureMode() throws IOException {
        Path verifierPath = Path.of("scripts", "verify-release-check-report.ps1");

        assertThat(verifierPath).exists().isRegularFile();

        String verifier = Files.readString(verifierPath, StandardCharsets.UTF_8);
        assertThat(verifier)
                .contains("[string]$ReportDirectory")
                .contains("[switch]$AllowFailed")
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("schemaVersion")
                .contains("releaseCandidateCommit")
                .contains("powerShellScriptSyntaxGate")
                .contains("environment")
                .contains("sha256-helpers.ps1")
                .contains("Get-Sha256Hex")
                .containsIgnoringCase("sha256")
                .contains("status is FAILED")
                .contains("Release check report verification passed");
    }

    @Test
    void releaseCheckReportVerifierRejectsDirtyPassedReportsUnlessExplicitlyAllowed() throws IOException {
        String verifier = Files.readString(Path.of("scripts", "verify-release-check-report.ps1"), StandardCharsets.UTF_8);

        assertThat(verifier)
                .contains("[switch]$AllowDirtyWorktree")
                .contains("function Assert-ReleaseCheckReportDirtyWorktreePolicy")
                .contains("allowDirtyWorktree")
                .contains("$reportAllowsDirtyWorktree")
                .contains("PASSED report was generated with -AllowDirtyWorktree")
                .contains("use -AllowDirtyWorktree only for local non-release investigation reports")
                .contains("Assert-ReleaseCheckReportDirtyWorktreePolicy -Report $report -Status $status");
    }

    @Test
    void releaseCheckReportVerifierRejectsReportsFromDifferentHeadCommit() throws IOException {
        String verifier = Files.readString(Path.of("scripts", "verify-release-check-report.ps1"), StandardCharsets.UTF_8);

        assertThat(verifier)
                .contains("function Assert-ReleaseCheckReportCommitMatchesHead")
                .contains("git rev-parse --short HEAD")
                .contains("releaseCandidateCommit matches current HEAD")
                .contains("does not match current HEAD")
                .contains("Assert-ReleaseCheckReportCommitMatchesHead -Report $report");
    }

    @Test
    void releaseCheckReportVerifierDoesNotTrustReportedRepositoryForArtifacts() throws IOException {
        String verifier = Files.readString(Path.of("scripts", "verify-release-check-report.ps1"), StandardCharsets.UTF_8);

        assertThat(verifier)
                .contains("Assert-ReleaseCheckReportRepository")
                .contains("reported repository matches current repository root")
                .contains("-RepositoryRoot $RepoRoot")
                .doesNotContain("-RepositoryRoot ([string](Get-ReleaseCheckReportObjectProperty -Object $report -Name \"repository\"))");
    }
}

package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PreproductionEvidenceIndexVerificationScriptConfigurationTest {

    @Test
    void evidenceIndexVerificationScriptChecksLocalEvidenceCompleteness() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-preprod-evidence-index.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EvidenceIndexPath")
                .contains("[string]$EvidenceDirectory")
                .contains("[switch]$RequireUploadedFallback")
                .contains("function Get-PreprodEvidenceIndexPath")
                .contains("function Add-EvidenceIndexCheck")
                .contains("function Format-EvidenceIndexVerificationReportValue")
                .contains("function Save-EvidenceIndexVerificationReport")
                .contains("function Assert-EvidenceIndexRequiredField")
                .contains("function Assert-EvidenceFileExists")
                .contains("function Assert-FallbackPackage")
                .contains("$verificationReportJsonPath = Join-Path $indexDirectory \"evidence-index.verify-report.json\"")
                .contains("$verificationReportMarkdownPath = Join-Path $indexDirectory \"evidence-index.verify-report.md\"")
                .contains("status = $Status")
                .contains("requireUploadedFallback = $RequireUploadedFallback.IsPresent")
                .contains("checks = @($checks)")
                .contains("Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8")
                .contains("Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8")
                .contains("Evidence index verification report")
                .contains("Verification report JSON")
                .contains("ConvertFrom-Json")
                .contains("summaryPath")
                .contains("reports")
                .contains("stepResults")
                .contains("fallbackPackages")
                .contains("ReadinessRunId")
                .contains("goNoGoVerdict")
                .contains("Failure triage index")
                .contains("Go / No-Go")
                .contains("uploadStatus")
                .contains("UPLOADED")
                .contains("PENDING")
                .contains("PASSED")
                .contains("FAILED");
    }

    @Test
    void releaseDocumentsReferenceEvidenceIndexVerification() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\verify-preprod-evidence-index.ps1")
                .contains("证据索引一致性")
                .contains("-RequireUploadedFallback")
                .contains("evidence-index.verify-report.json")
                .contains("evidence-index.verify-report.md");
        assertThat(checklist)
                .contains(".\\scripts\\verify-preprod-evidence-index.ps1")
                .contains("证据索引一致性")
                .contains("evidence-index.verify-report.json")
                .contains("evidence-index.verify-report.md");
        assertThat(audit)
                .contains(".\\scripts\\verify-preprod-evidence-index.ps1")
                .contains("证据索引一致性")
                .contains("-RequireUploadedFallback")
                .contains("evidence-index.verify-report.json")
                .contains("evidence-index.verify-report.md");
    }

    @Test
    void preproductionAcceptanceGateScriptChainsEvidenceAndUploadChecks() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-preprod-acceptance-gate.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EvidenceDirectory")
                .contains("[string]$EvidenceIndexPath")
                .contains("[string]$BaseUrl")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$ReadinessRunId")
                .contains("[string]$OutputPath")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("function Invoke-GateStep")
                .contains("function Get-GateFallbackManifestPaths")
                .contains("function Get-GateReadinessHeaders")
                .contains("function Register-GateReadinessEvidence")
                .contains("function Add-GateReportSection")
                .contains("function Save-GateReport")
                .contains("function Get-GateJsonReportPath")
                .contains("function Save-GateJsonReport")
                .contains("Register-ReadinessEvidenceWithOfflineFallback")
                .contains("PREPROD_APPROVAL_GATE")
                .contains("审批前总门禁")
                .contains("verify-preprod-evidence-index.ps1")
                .contains("replay-readiness-evidence.ps1")
                .contains("verify-readiness-evidence-upload.ps1")
                .contains("-RequireUploadedFallback")
                .contains("-ValidateOnly")
                .contains("preprod-acceptance-gate.md")
                .contains("preprod-acceptance-gate.json")
                .contains("schemaVersion = 1")
                .contains("verdict = $Verdict")
                .contains("readinessStatus = (Get-GateReadinessStatus -Verdict $Verdict)")
                .contains("failureReason = (Get-GateFailureReason -Verdict $Verdict)")
                .contains("steps = @($gateSteps")
                .contains("ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $JsonOutputPath -Encoding UTF8")
                .contains("Gate JSON report written to")
                .contains("READY_FOR_APPROVAL")
                .contains("BLOCKED");

        assertThat(deployment)
                .contains(".\\scripts\\verify-preprod-acceptance-gate.ps1")
                .contains("READY_FOR_APPROVAL")
                .contains("PREPROD_APPROVAL_GATE")
                .contains("preprod-acceptance-gate.json");
        assertThat(checklist)
                .contains(".\\scripts\\verify-preprod-acceptance-gate.ps1")
                .contains("READY_FOR_APPROVAL")
                .contains("PREPROD_APPROVAL_GATE")
                .contains("preprod-acceptance-gate.json");
        assertThat(audit)
                .contains(".\\scripts\\verify-preprod-acceptance-gate.ps1")
                .contains("READY_FOR_APPROVAL")
                .contains("PREPROD_APPROVAL_GATE")
                .contains("preprod-acceptance-gate.json");
    }

    @Test
    void preproductionAcceptanceGateReportVerificationScriptRechecksMarkdownAndJsonSidecar() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-preprod-acceptance-gate-report.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$GateReportPath")
                .contains("[string]$EvidenceDirectory")
                .contains("[switch]$AllowBlocked")
                .contains("function Add-GateReportVerificationCheck")
                .contains("function Get-PreprodAcceptanceGateReportPath")
                .contains("function Get-GateReportJsonPath")
                .contains("function Assert-GateReportJson")
                .contains("function Assert-GateReportMarkdown")
                .contains("function Save-GateReportVerificationReport")
                .contains("preprod-acceptance-gate.md")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("preprod-acceptance-gate.verify-report.md")
                .contains("schemaVersion")
                .contains("verdict")
                .contains("readinessStatus")
                .contains("stepCount")
                .contains("failedStepCount")
                .contains("READY_FOR_APPROVAL")
                .contains("BLOCKED")
                .contains("Get-Content -LiteralPath $ResolvedGateReportPath -Raw")
                .contains("ConvertFrom-Json")
                .contains("Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8")
                .contains("Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8")
                .contains("Preproduction acceptance gate report verification");

        assertThat(deployment)
                .contains(".\\scripts\\verify-preprod-acceptance-gate-report.ps1")
                .contains("审批前总门禁报告复验")
                .contains("preprod-acceptance-gate.verify-report.json");
        assertThat(checklist)
                .contains(".\\scripts\\verify-preprod-acceptance-gate-report.ps1")
                .contains("审批前总门禁报告复验")
                .contains("preprod-acceptance-gate.verify-report.json");
        assertThat(audit)
                .contains(".\\scripts\\verify-preprod-acceptance-gate-report.ps1")
                .contains("审批前总门禁报告复验")
                .contains("preprod-acceptance-gate.verify-report.json");
    }

    @Test
    void readinessReleaseDecisionScriptRequiresApprovalGateBeforeGoDecision() throws IOException {
        Path scriptPath = Path.of("scripts", "decide-readiness-release.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EvidenceDirectory")
                .contains("[string]$EvidenceIndexPath")
                .contains("[string]$BaseUrl")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$ReadinessRunId")
                .contains("[string]$OutputPath")
                .contains("[switch]$DryRun")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("function Get-ReadinessDecisionHeaders")
                .contains("function Get-ReadinessDecisionRunDetail")
                .contains("function Assert-ReadinessDecisionPrerequisites")
                .contains("function Save-ReadinessDecisionReport")
                .contains("/api/system/readiness/runs/")
                .contains("/decision")
                .contains("PREPROD_APPROVAL_GATE")
                .contains("READY_TO_DECIDE")
                .contains("DECIDED_GO")
                .contains("BLOCKED")
                .contains("decision = \"GO\"")
                .contains("status = \"PASSED\"")
                .contains("readiness-release-decision.md");

        assertThat(deployment)
                .contains(".\\scripts\\decide-readiness-release.ps1")
                .contains("最终发布决策")
                .contains("DECIDED_GO");
        assertThat(checklist)
                .contains(".\\scripts\\decide-readiness-release.ps1")
                .contains("最终发布决策");
        assertThat(audit)
                .contains(".\\scripts\\decide-readiness-release.ps1")
                .contains("最终发布决策");
    }

    @Test
    void releaseEvidenceBundleScriptPackagesApprovedEvidenceWithChecksums() throws IOException {
        Path scriptPath = Path.of("scripts", "export-release-evidence-bundle.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EvidenceDirectory")
                .contains("[string]$EvidenceIndexPath")
                .contains("[string]$OutputPath")
                .contains("[string]$ReleaseCheckReportDirectory")
                .contains("[switch]$AllowBlocked")
                .contains("function Get-ReleaseEvidenceIndexPath")
                .contains("function Assert-ReleaseEvidenceBundlePrerequisites")
                .contains("function Assert-ReleaseEvidenceJsonPropertyEquals")
                .contains("function Get-ReleaseEvidenceMarkdownTableValue")
                .contains("function Assert-ReleaseEvidencePreprodGateVerificationMarkdownStatus")
                .contains("function Assert-ReleaseCheckReports")
                .contains("function New-ReleaseEvidenceSourceFile")
                .contains("function Save-ReleaseEvidenceBundleManifest")
                .contains("function Save-ReleaseEvidenceBundleSummary")
                .contains("function Save-ReleaseEvidenceArtifactsIndex")
                .contains("function Invoke-ReleaseEvidenceArtifactsIndexVerifier")
                .contains("function Invoke-ReleaseEvidenceBundleVerifier")
                .contains("Compress-Archive")
                .containsIgnoringCase("sha256")
                .contains("[string]$SummaryJsonPath")
                .contains("$summaryPath = \"$OutputPath.summary.md\"")
                .contains("$summaryJsonPath = \"$OutputPath.summary.json\"")
                .contains("$summaryJson = [ordered]@{")
                .contains("schemaVersion = 1")
                .contains("summaryMarkdownPath = $SummaryPath")
                .contains("failedPrerequisiteCheckCount = $failedCheckCount")
                .contains("releaseCheck = [ordered]@{")
                .contains("$summaryJson | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $SummaryJsonPath -Encoding UTF8")
                .contains("release-evidence-bundle*.zip.summary.json")
                .contains("release-evidence-artifacts-index.json")
                .contains("release-evidence-artifacts-index.md")
                .contains("release-evidence-artifacts-index.verify-report.json")
                .contains("release-evidence-artifacts-index.verify-report.md")
                .contains("Release evidence bundle summary")
                .contains("Release evidence artifacts index")
                .contains("GITHUB_STEP_SUMMARY")
                .contains("Set-Content -LiteralPath $SummaryPath -Encoding UTF8")
                .contains("Add-Content -LiteralPath $githubStepSummary -Value $summaryMarkdown -Encoding UTF8")
                .contains("verify-release-evidence-bundle.ps1")
                .contains("verify-release-evidence-artifacts-index.ps1")
                .contains("$verifierParams = @{ BundlePath = $BundlePath; Sha256Path = $Sha256Path }")
                .contains("& $verifierPath @verifierParams")
                .contains("Release evidence bundle self-verification")
                .contains("Release evidence artifacts index verification")
                .contains("Invoke-ReleaseEvidenceBundleVerifier -BundlePath $OutputPath -Sha256Path $sha256Path -AllowBlocked:$AllowBlocked")
                .contains("Save-ReleaseEvidenceBundleSummary -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath -BundlePath $OutputPath -Sha256Path $sha256Path -BundleStatus $bundleStatus -BundleSha256 $bundleHash.Hash -SourceFileCount $sourceEntries.Count")
                .contains("$artifactsIndexJsonPath = Join-Path $evidenceRoot \"release-evidence-artifacts-index.json\"")
                .contains("$artifactsIndexMarkdownPath = Join-Path $evidenceRoot \"release-evidence-artifacts-index.md\"")
                .contains("$verifyReportJsonPath = \"$OutputPath.verify-report.json\"")
                .contains("$verifyReportMarkdownPath = \"$OutputPath.verify-report.md\"")
                .contains("Save-ReleaseEvidenceArtifactsIndex -IndexJsonPath $artifactsIndexJsonPath -IndexMarkdownPath $artifactsIndexMarkdownPath -BundlePath $OutputPath -Sha256Path $sha256Path -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath")
                .contains("Invoke-ReleaseEvidenceArtifactsIndexVerifier -ArtifactsIndexPath $artifactsIndexJsonPath")
                .contains("Artifacts index JSON written to")
                .contains("Summary JSON written to")
                .contains("[long]::TryParse")
                .contains("Cannot parse evidence index")
                .contains("release-evidence-bundle-manifest.json")
                .contains("readiness-release-decision.md")
                .contains("preprod-acceptance-gate.md")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("preprod-acceptance-gate.verify-report.md")
                .contains("Preproduction approval gate JSON verdict")
                .contains("Preproduction approval gate report verification JSON status")
                .contains("Preproduction approval gate report verification Markdown")
                .contains("Preproduction approval gate report verification Markdown status")
                .contains("Assert-ReleaseEvidencePreprodGateVerificationMarkdownStatus -EvidenceRoot $EvidenceRoot")
                .contains("preprodAcceptanceGateJson")
                .contains("preprodAcceptanceGateVerificationJson")
                .contains("preprodAcceptanceGateVerificationMarkdown")
                .contains("DECIDED_GO")
                .contains("READY_FOR_APPROVAL")
                .contains("goNoGoVerdict")
                .contains("uploadStatus")
                .contains("UPLOADED")
                .contains("release-check-report.json")
                .contains("release-check-report.md")
                .contains("release-evidence-bundle")
                .doesNotContain("& $verifierPath @verifierArgs");

        int shaWrite = script.indexOf("Set-Content -LiteralPath $sha256Path");
        int selfVerify = script.indexOf("Invoke-ReleaseEvidenceBundleVerifier -BundlePath $OutputPath -Sha256Path $sha256Path -AllowBlocked:$AllowBlocked");
        int summaryWrite = script.indexOf("Save-ReleaseEvidenceBundleSummary -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath -BundlePath $OutputPath -Sha256Path $sha256Path -BundleStatus $bundleStatus -BundleSha256 $bundleHash.Hash -SourceFileCount $sourceEntries.Count");
        int artifactsIndexWrite = script.indexOf("Save-ReleaseEvidenceArtifactsIndex -IndexJsonPath $artifactsIndexJsonPath -IndexMarkdownPath $artifactsIndexMarkdownPath -BundlePath $OutputPath -Sha256Path $sha256Path -SummaryPath $summaryPath -SummaryJsonPath $summaryJsonPath");
        int artifactsIndexVerify = script.indexOf("Invoke-ReleaseEvidenceArtifactsIndexVerifier -ArtifactsIndexPath $artifactsIndexJsonPath");
        int finalStatus = script.indexOf("[release-evidence-bundle] Bundle status:");
        assertThat(shaWrite).isLessThan(selfVerify);
        assertThat(summaryWrite).isLessThan(selfVerify);
        assertThat(summaryWrite).isLessThan(finalStatus);
        assertThat(selfVerify).isLessThan(finalStatus);
        assertThat(selfVerify).isLessThan(artifactsIndexWrite);
        assertThat(artifactsIndexWrite).isLessThan(finalStatus);
        assertThat(artifactsIndexWrite).isLessThan(artifactsIndexVerify);
        assertThat(artifactsIndexVerify).isLessThan(finalStatus);

        assertThat(deployment)
                .contains(".\\scripts\\export-release-evidence-bundle.ps1")
                .contains("发布证据归档")
                .contains("SHA-256")
                .contains("release-check-report.json")
                .contains("归档包自复验")
                .contains("发布证据包摘要")
                .contains("发布证据目录索引")
                .contains("发布证据目录索引复验")
                .contains("<zip>.summary.json")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("总门禁报告复验 Markdown Status")
                .contains("release-evidence-artifacts-index.json")
                .contains("GITHUB_STEP_SUMMARY");
        assertThat(checklist)
                .contains(".\\scripts\\export-release-evidence-bundle.ps1")
                .contains("发布证据归档")
                .contains("release-check-report.json")
                .contains("归档包自复验")
                .contains("发布证据包摘要")
                .contains("<zip>.summary.json")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("总门禁报告复验 Markdown Status")
                .contains("release-evidence-artifacts-index.json")
                .contains("发布证据目录索引复验");
        assertThat(audit)
                .contains(".\\scripts\\export-release-evidence-bundle.ps1")
                .contains("发布证据归档")
                .contains("release-check-report.json")
                .contains("归档包自复验")
                .contains("发布证据包摘要")
                .contains("<zip>.summary.json")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("总门禁报告复验 Markdown Status")
                .contains("release-evidence-artifacts-index.json")
                .contains("发布证据目录索引复验");
    }

    @Test
    void releaseEvidenceBundleScriptsRejectDirtyWorktreeReleaseCheckReports() throws IOException {
        String exporter = Files.readString(Path.of("scripts", "export-release-evidence-bundle.ps1"), StandardCharsets.UTF_8);
        String verifier = Files.readString(Path.of("scripts", "verify-release-evidence-bundle.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(exporter)
                .contains("function Assert-ReleaseCheckReportDirtyWorktreePolicy")
                .contains("Get-ReleaseEvidenceObjectProperty -Object $ReleaseCheckReport -Name \"allowDirtyWorktree\"")
                .contains("$reportAllowsDirtyWorktree")
                .contains("Release check report dirty worktree policy")
                .contains("release-check PASSED report was generated with -AllowDirtyWorktree")
                .contains("Assert-ReleaseCheckReportDirtyWorktreePolicy -ReleaseCheckReport $releaseCheckReport -ReportStatus $reportStatus");

        assertThat(verifier)
                .contains("function Assert-ReleaseEvidenceBundleReleaseCheckDirtyWorktreePolicy")
                .contains("Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name \"allowDirtyWorktree\"")
                .contains("$reportAllowsDirtyWorktree")
                .contains("Release check report dirty worktree policy")
                .contains("release-check PASSED report was generated with -AllowDirtyWorktree")
                .contains("Assert-ReleaseEvidenceBundleReleaseCheckDirtyWorktreePolicy -ExtractRoot $ExtractRoot");

        assertThat(deployment)
                .contains("发布证据归档")
                .contains("allowDirtyWorktree=true")
                .contains("默认拒绝")
                .contains("发布证据归档和归档包复验都会拒绝");

        assertThat(checklist)
                .contains("发布证据归档")
                .contains("allowDirtyWorktree=true")
                .contains("默认拒绝")
                .contains("发布证据归档和归档包复验都会拒绝");
    }

    @Test
    void releaseEvidenceBundleSummaryAndArtifactsIndexExposeDirtyWorktreeFlag() throws IOException {
        String exporter = Files.readString(Path.of("scripts", "export-release-evidence-bundle.ps1"), StandardCharsets.UTF_8);
        String bundleVerifier = Files.readString(Path.of("scripts", "verify-release-evidence-bundle.ps1"), StandardCharsets.UTF_8);
        String artifactsIndexVerifier = Files.readString(Path.of("scripts", "verify-release-evidence-artifacts-index.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(exporter)
                .contains("AllowDirtyWorktree = \"UNKNOWN\"")
                .contains("Get-ReleaseEvidenceObjectProperty -Object $report -Name \"allowDirtyWorktree\"")
                .contains("allowDirtyWorktree = $releaseCheck.AllowDirtyWorktree")
                .contains("Release check allow dirty worktree")
                .contains("| Release check allow dirty worktree | $(Format-ReleaseEvidenceSummaryValue $releaseCheck.AllowDirtyWorktree) |");

        assertThat(bundleVerifier)
                .contains("Summary JSON releaseCheck.allowDirtyWorktree")
                .contains("Get-ReleaseEvidenceBundleObjectProperty -Object $releaseCheckReport -Name \"allowDirtyWorktree\"")
                .contains("Get-ReleaseEvidenceBundleObjectProperty -Object $summaryJson.releaseCheck -Name \"allowDirtyWorktree\"")
                .contains("$expectedReleaseCheckStatus")
                .contains("$expectedReleaseCandidateCommit")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Release check status\"")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Release candidate commit\"")
                .contains("Release check allow dirty worktree")
                .contains("Assert-ReleaseEvidenceBundleSummaryMarkdown -ResolvedBundlePath $resolvedBundlePath -ResolvedSha256Path $resolvedSha256Path -ExtractRoot $extractInfo.Path -Manifest $manifest -ExpectedBundleSha256 $bundleSha256");

        assertThat(artifactsIndexVerifier)
                .contains("-PropertyName \"releaseCheck.allowDirtyWorktree\"")
                .contains("-Field \"Release check allow dirty worktree\"")
                .contains("Field = \"Release check allow dirty worktree\"")
                .contains("Get-ReleaseEvidenceArtifactsIndexObjectProperty -Object $releaseCheck -Name \"allowDirtyWorktree\"");

        assertThat(deployment)
                .contains("Release check allow dirty worktree")
                .contains("allowDirtyWorktree");
        assertThat(checklist)
                .contains("Release check allow dirty worktree")
                .contains("allowDirtyWorktree");
    }

    @Test
    void releaseEvidenceArtifactsIndexVerificationScriptRechecksGeneratedSidecars() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-release-evidence-artifacts-index.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$ArtifactsIndexPath")
                .contains("[string]$EvidenceDirectory")
                .contains("function Save-ReleaseEvidenceArtifactsIndexVerificationReport")
                .contains("function Get-ReleaseEvidenceRequiredArtifactRoles")
                .contains("function Get-ReleaseEvidenceArtifactsIndexArtifactByRole")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexJson")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexRequiredRoles")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexDuplicateRoles")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexRoleStatus")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexRoleStatuses")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexArtifactJsonProperty")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexJsonSemantics")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexSummaryJsonSemantics")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexBundleSha256SidecarSemantics")
                .contains("function Get-ReleaseEvidenceArtifactsIndexMarkdownTableValue")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownSemantics")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownSemantics")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownSemantics")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexEvidenceDirectory")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexBundlePath")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexArtifactFileName")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexArtifactLastWriteTimeUtc")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexMarkdown")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRow")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRow")
                .contains("function Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows")
                .contains("release-evidence-artifacts-index.json")
                .contains("release-evidence-artifacts-index.md")
                .contains("release-evidence-artifacts-index.verify-report.json")
                .contains("release-evidence-artifacts-index.verify-report.md")
                .contains("schemaVersion")
                .contains("artifactCount")
                .contains("missingArtifactCount")
                .contains("verificationStatus")
                .contains("Required artifact role")
                .contains("bundle")
                .contains("bundleSha256")
                .contains("summaryMarkdown")
                .contains("summaryJson")
                .contains("verificationReportJson")
                .contains("verificationReportMarkdown")
                .contains("preprodAcceptanceGateJson")
                .contains("preprodAcceptanceGateVerificationJson")
                .contains("preprodAcceptanceGateVerificationMarkdown")
                .contains("Assert-ReleaseEvidenceArtifactsIndexRequiredRoles -Artifacts $artifacts")
                .contains("Assert-ReleaseEvidenceArtifactsIndexDuplicateRoles -Artifacts $artifacts")
                .contains("Assert-ReleaseEvidenceArtifactsIndexRoleStatuses -Index $index -Artifacts $artifacts")
                .contains("Assert-ReleaseEvidenceArtifactsIndexEvidenceDirectory -Index $index -IndexDirectory $IndexDirectory")
                .contains("Assert-ReleaseEvidenceArtifactsIndexBundlePath -Index $index -Artifacts $artifacts")
                .contains("-Role \"bundle\" -ExpectedStatus $bundleStatus")
                .contains("-Role \"bundleSha256\" -ExpectedStatus \"PRESENT\"")
                .contains("-Role \"verificationReportJson\" -ExpectedStatus $verificationStatus")
                .contains("-Role \"verificationReportMarkdown\" -ExpectedStatus $verificationStatus")
                .contains("-Role \"preprodAcceptanceGateVerificationJson\" -ExpectedStatus \"PASSED\"")
                .contains("-Role \"preprodAcceptanceGateVerificationMarkdown\" -ExpectedStatus \"PASSED\"")
                .contains("Artifact role $Role status")
                .contains("Assert-ReleaseEvidenceArtifactsIndexJsonSemantics -Index $index -Artifacts $artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role $Role JSON $PropertyName")
                .contains("-Role \"summaryJson\" -IndexDirectory $IndexDirectory -PropertyName \"bundleStatus\" -ExpectedValue $bundleStatus")
                .contains("Assert-ReleaseEvidenceArtifactsIndexSummaryJsonSemantics -Index $Index -Artifacts $Artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role summaryJson JSON $PropertyName")
                .contains("-PropertyName \"bundleSha256\"")
                .contains("-PropertyName \"releaseCheck.status\"")
                .contains("-PropertyName \"releaseCheck.releaseCandidateCommit\"")
                .contains("Assert-ReleaseEvidenceArtifactsIndexBundleSha256SidecarSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role bundleSha256 content")
                .contains("Assert-ReleaseEvidenceArtifactsIndexSummaryMarkdownSemantics -Index $index -Artifacts $artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role summaryMarkdown Markdown $Field")
                .contains("-Field \"Bundle status\"")
                .contains("-Field \"Bundle SHA-256\"")
                .contains("-Field \"Release check status\"")
                .contains("-Field \"Release candidate commit\"")
                .contains("Assert-ReleaseEvidenceArtifactsIndexVerificationReportMarkdownSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role verificationReportMarkdown Markdown $Field")
                .contains("-Field \"Status\"")
                .contains("-Field \"Bundle path\"")
                .contains("-Field \"SHA-256 file\"")
                .contains("Assert-ReleaseEvidenceArtifactsIndexPreprodAcceptanceGateVerificationMarkdownSemantics -Artifacts $Artifacts -IndexDirectory $IndexDirectory")
                .contains("Artifact role preprodAcceptanceGateVerificationMarkdown Markdown $Field")
                .contains("-Role \"verificationReportJson\" -IndexDirectory $IndexDirectory -PropertyName \"status\" -ExpectedValue $verificationStatus")
                .contains("-Role \"preprodAcceptanceGateJson\" -IndexDirectory $IndexDirectory -PropertyName \"verdict\" -ExpectedValue \"READY_FOR_APPROVAL\"")
                .contains("-Role \"preprodAcceptanceGateVerificationJson\" -IndexDirectory $IndexDirectory -PropertyName \"status\" -ExpectedValue \"PASSED\"")
                .contains("Artifacts index evidenceDirectory")
                .contains("Artifacts index bundlePath")
                .contains("Assert-ReleaseEvidenceArtifactsIndexArtifactFileName -Artifact $artifact -Role $role -PathValue $pathValue")
                .contains("Assert-ReleaseEvidenceArtifactsIndexArtifactLastWriteTimeUtc -Artifact $artifact -Role $role -File $file")
                .contains("Artifact $role fileName")
                .contains("Artifact $role lastWriteTimeUtc")
                .contains("Assert-ReleaseEvidenceArtifactsIndexMarkdownSummaryRows -Markdown $markdown -Index $Index")
                .contains("Artifacts index Markdown summary field $Field")
                .contains("Assert-ReleaseEvidenceArtifactsIndexMarkdownArtifactRows -Markdown $markdown -Index $Index")
                .contains("Artifacts index Markdown artifact row $role")
                .contains("Duplicate artifact role $role")
                .contains("Get-Sha256Hex")
                .contains("sha256-helpers.ps1")
                .containsIgnoringCase("sha256")
                .contains("Release evidence artifacts index")
                .contains("Artifacts index verification passed")
                .contains("Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8")
                .contains("Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8");

        assertThat(deployment)
                .contains(".\\scripts\\verify-release-evidence-artifacts-index.ps1")
                .contains("发布证据目录索引复验")
                .contains("必需 artifact role")
                .contains("重复 artifact role")
                .contains("artifact role 状态语义")
                .contains("artifact JSON 内容语义")
                .contains("summary JSON 关键字段语义")
                .contains("bundleSha256 sidecar 内容语义")
                .contains("summaryMarkdown 内容语义")
                .contains("verificationReportMarkdown 内容语义")
                .contains("preprodAcceptanceGateVerificationMarkdown 内容语义")
                .contains("顶层 evidenceDirectory / bundlePath 一致性")
                .contains("artifact 文件名和 UTC 修改时间一致性")
                .contains("Markdown summary 字段一致性")
                .contains("Markdown artifact 行一致性")
                .contains("release-evidence-artifacts-index.verify-report.json");
        assertThat(checklist)
                .contains(".\\scripts\\verify-release-evidence-artifacts-index.ps1")
                .contains("发布证据目录索引复验")
                .contains("必需 artifact role")
                .contains("重复 artifact role")
                .contains("artifact role 状态语义")
                .contains("artifact JSON 内容语义")
                .contains("summary JSON 关键字段语义")
                .contains("bundleSha256 sidecar 内容语义")
                .contains("summaryMarkdown 内容语义")
                .contains("verificationReportMarkdown 内容语义")
                .contains("preprodAcceptanceGateVerificationMarkdown 内容语义")
                .contains("顶层 evidenceDirectory / bundlePath 一致性")
                .contains("artifact 文件名和 UTC 修改时间一致性")
                .contains("Markdown summary 字段一致性")
                .contains("Markdown artifact 行一致性")
                .contains("release-evidence-artifacts-index.verify-report.json");
        assertThat(audit)
                .contains(".\\scripts\\verify-release-evidence-artifacts-index.ps1")
                .contains("发布证据目录索引复验")
                .contains("必需 artifact role")
                .contains("重复 artifact role")
                .contains("artifact role 状态语义")
                .contains("artifact JSON 内容语义")
                .contains("summary JSON 关键字段语义")
                .contains("bundleSha256 sidecar 内容语义")
                .contains("summaryMarkdown 内容语义")
                .contains("verificationReportMarkdown 内容语义")
                .contains("preprodAcceptanceGateVerificationMarkdown 内容语义")
                .contains("顶层 evidenceDirectory / bundlePath 一致性")
                .contains("artifact 文件名和 UTC 修改时间一致性")
                .contains("Markdown summary 字段一致性")
                .contains("Markdown artifact 行一致性")
                .contains("release-evidence-artifacts-index.verify-report.json");
    }

    @Test
    void releaseEvidenceBundleVerificationScriptRechecksArchiveManifestAndChecksums() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-release-evidence-bundle.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "archive", "2026-06-stale", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$BundlePath")
                .contains("[string]$Sha256Path")
                .contains("[string]$ExtractDirectory")
                .contains("[switch]$AllowBlocked")
                .contains("function Add-ReleaseEvidenceBundleVerificationCheck")
                .contains("function Assert-ReleaseEvidenceBundleSha256")
                .contains("function Expand-ReleaseEvidenceBundle")
                .contains("function Assert-ReleaseEvidenceBundleManifest")
                .contains("function Assert-ReleaseEvidenceBundleSourceFiles")
                .contains("function Get-ReleaseEvidenceBundleObjectProperty")
                .contains("function Get-ReleaseEvidenceBundleMarkdownTableValue")
                .contains("function Assert-ReleaseEvidenceBundleRequiredText")
                .contains("function Assert-ReleaseEvidenceBundleRequiredJsonProperty")
                .contains("function Assert-ReleaseEvidenceBundlePreprodGateVerificationMarkdownStatus")
                .contains("function Assert-ReleaseEvidenceBundleRequiredEvidence")
                .contains("function Assert-ReleaseEvidenceBundleSummaryJson")
                .contains("function Assert-ReleaseEvidenceBundleSummaryMarkdownValue")
                .contains("function Assert-ReleaseEvidenceBundleSummaryMarkdown")
                .contains("function Save-ReleaseEvidenceBundleVerificationReport")
                .contains("release-evidence-bundle-manifest.json")
                .contains("preprod-acceptance-gate.md")
                .contains("preprod-acceptance-gate.json")
                .contains("preprod-acceptance-gate.verify-report.json")
                .contains("preprod-acceptance-gate.verify-report.md")
                .contains("readiness-release-decision.md")
                .contains("release-check/release-check-report.md")
                .contains("$ResolvedBundlePath.summary.json")
                .contains("$ResolvedBundlePath.summary.md")
                .contains("$verificationReportJsonPath = \"$resolvedBundlePath.verify-report.json\"")
                .contains("$verificationReportMarkdownPath = \"$resolvedBundlePath.verify-report.md\"")
                .contains("Preproduction approval gate report")
                .contains("Preproduction approval gate JSON verdict")
                .contains("Preproduction approval gate report verification JSON status")
                .contains("Preproduction approval gate report verification Markdown")
                .contains("Preproduction approval gate report verification Markdown status")
                .contains("Readiness release decision report")
                .contains("Release check report Markdown")
                .contains("-RequiredText \"Release Check Report\"")
                .contains("Summary JSON")
                .contains("Summary Markdown")
                .contains("schemaVersion")
                .contains("status = $Status")
                .contains("checks = @($verificationChecks)")
                .contains("bundleStatus")
                .contains("bundleSha256")
                .contains("sourceFiles")
                .contains("sourceFileCount")
                .contains("failedPrerequisiteCheckCount")
                .contains("releaseCheck.status")
                .contains("Get-Sha256Hex")
                .contains("sha256-helpers.ps1")
                .containsIgnoringCase("sha256")
                .contains("Expand-Archive")
                .contains("READY")
                .contains("BLOCKED")
                .contains("READY_FOR_APPROVAL")
                .contains("DECIDED_GO")
                .contains("PASSED")
                .contains("Assert-ReleaseEvidenceBundlePreprodGateVerificationMarkdownStatus -ExtractRoot $ExtractRoot")
                .contains("Assert-ReleaseEvidenceBundleRequiredEvidence -ExtractRoot $extractInfo.Path")
                .contains("Assert-ReleaseEvidenceBundleSummaryJson -ResolvedBundlePath $resolvedBundlePath -ExtractRoot $extractInfo.Path -Manifest $manifest -ExpectedBundleSha256 $bundleSha256")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Bundle path\"")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"SHA-256 file\"")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Release check status\"")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Release candidate commit\"")
                .contains("Get-ReleaseEvidenceBundleMarkdownTableValue -Markdown $summaryMarkdown -Field \"Release check allow dirty worktree\"")
                .contains("Summary Markdown field '$Field' is")
                .contains("Assert-ReleaseEvidenceBundleSummaryMarkdown -ResolvedBundlePath $resolvedBundlePath -ResolvedSha256Path $resolvedSha256Path -ExtractRoot $extractInfo.Path -Manifest $manifest -ExpectedBundleSha256 $bundleSha256")
                .contains("Save-ReleaseEvidenceBundleVerificationReport -ReportJsonPath $verificationReportJsonPath -ReportMarkdownPath $verificationReportMarkdownPath -Status $verificationStatus -FailureReason $verificationFailureReason")
                .contains("Set-Content -LiteralPath $ReportJsonPath -Encoding UTF8")
                .contains("Set-Content -LiteralPath $ReportMarkdownPath -Encoding UTF8")
                .contains("Verification report JSON");

        assertThat(deployment)
                .contains(".\\scripts\\verify-release-evidence-bundle.ps1")
                .contains("发布证据复验")
                .contains("关键证据语义复验")
                .contains("总门禁报告复验 Markdown Status")
                .contains("READY_FOR_APPROVAL")
                .contains("DECIDED_GO")
                .contains("发布证据包摘要复验")
                .contains("<zip>.summary.json")
                .contains("<zip>.verify-report.json")
                .contains("<zip>.verify-report.md");
        assertThat(checklist)
                .contains(".\\scripts\\verify-release-evidence-bundle.ps1")
                .contains("发布证据复验")
                .contains("关键证据语义复验")
                .contains("总门禁报告复验 Markdown Status")
                .contains("READY_FOR_APPROVAL")
                .contains("DECIDED_GO")
                .contains("发布证据包摘要复验")
                .contains("<zip>.summary.json")
                .contains("<zip>.verify-report.json")
                .contains("<zip>.verify-report.md");
        assertThat(audit)
                .contains(".\\scripts\\verify-release-evidence-bundle.ps1")
                .contains("发布证据复验")
                .contains("关键证据语义复验")
                .contains("总门禁报告复验 Markdown Status")
                .contains("READY_FOR_APPROVAL")
                .contains("DECIDED_GO")
                .contains("发布证据包摘要复验")
                .contains("<zip>.summary.json")
                .contains("<zip>.verify-report.json")
                .contains("<zip>.verify-report.md");
    }
}

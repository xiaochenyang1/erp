package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessAcceptanceScriptRegistrationConfigurationTest {

    @Test
    void acceptanceScriptsCanRegisterEvidenceIntoReadinessRun() throws IOException {
        assertHelperCoversReadinessApi();
        assertScriptRegistersReadinessEvidence("preprod-acceptance.ps1",
                "RELEASE_GATE",
                "DOCKER_COMPOSE_HEALTH",
                "AUTH_SMOKE",
                "PREPROD_ACCEPTANCE");
        assertScriptRegistersReadinessEvidence("business-smoke.ps1", "BUSINESS_SMOKE");
        assertScriptRegistersReadinessEvidence("purchase-to-payment-acceptance.ps1", "PURCHASE_TO_PAYMENT");
        assertScriptRegistersReadinessEvidence("sales-to-cash-acceptance.ps1", "SALES_TO_RECEIPT");
        assertScriptRegistersReadinessEvidence("production-manufacturing-acceptance.ps1", "PRODUCTION_MANUFACTURING");
    }

    @Test
    void releaseDocumentsExplainReadinessRunEvidenceRegistration() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains("-ReadinessRunId")
                .contains("/api/system/readiness");
        assertThat(checklist)
                .contains("-ReadinessRunId")
                .contains("系统内预生产验收记录");
        assertThat(audit)
                .contains("-ReadinessRunId")
                .contains("readiness");
    }

    @Test
    void preproductionScriptCanCreateReadinessRunBeforeRegisteringEvidence() throws IOException {
        String helper = Files.readString(Path.of("scripts", "readiness-evidence.ps1"), StandardCharsets.UTF_8);
        String script = Files.readString(Path.of("scripts", "preprod-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);

        assertThat(helper)
                .contains("function New-ReadinessRun")
                .contains("/api/system/readiness/runs")
                .contains("generateDefaultItems = $true")
                .contains("recordPreflightEvidence = $true");
        assertThat(script)
                .contains("[switch]$CreateReadinessRun")
                .contains("[string]$ReadinessReleaseVersion")
                .contains("New-ReadinessRun")
                .contains("Readiness run creation");
        assertThat(deployment)
                .contains("-CreateReadinessRun")
                .contains("自动创建 readiness 运行单");
    }

    @Test
    void readinessEvidenceRegistrationUploadsMarkdownReportAsAttachment() throws IOException {
        String helper = Files.readString(Path.of("scripts", "readiness-evidence.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);

        assertThat(helper)
                .contains("function New-ReadinessAttachment")
                .contains("/api/system/attachments")
                .contains("MultipartFormDataContent")
                .contains("text/markdown")
                .contains("attachmentBusinessType = \"SYSTEM_ATTACHMENT\"")
                .contains("attachmentBusinessId = $attachmentId")
                .contains("evidenceType = $evidenceType");
        assertThat(deployment)
                .contains("system:attachment:manage")
                .contains("Markdown 原文附件");
    }

    @Test
    void readinessEvidenceRegistrationWritesOfflineFallbackPackageWhenUploadFails() throws IOException {
        String helper = Files.readString(Path.of("scripts", "readiness-evidence.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(helper)
                .contains("function Save-ReadinessEvidenceFallbackPackage")
                .contains("function Register-ReadinessEvidenceWithOfflineFallback")
                .contains("readiness-evidence-pending-upload.json")
                .contains("readiness-evidence.md")
                .contains("registrationFailure")
                .contains("evidenceDetailFile")
                .contains("Set-Content -LiteralPath $manifestPath")
                .contains("Set-Content -LiteralPath $detailPath")
                .contains("ConvertTo-Json -Depth 20")
                .contains("Offline fallback package written");

        assertScriptUsesReadinessOfflineFallback("preprod-acceptance.ps1");
        assertScriptUsesReadinessOfflineFallback("business-smoke.ps1");
        assertScriptUsesReadinessOfflineFallback("purchase-to-payment-acceptance.ps1");
        assertScriptUsesReadinessOfflineFallback("sales-to-cash-acceptance.ps1");
        assertScriptUsesReadinessOfflineFallback("production-manufacturing-acceptance.ps1");
        assertScriptUsesReadinessOfflineFallback("preprod-full-acceptance.ps1");

        assertThat(deployment)
                .contains("离线 fallback")
                .contains("待补传")
                .contains("readiness-evidence-pending-upload.json");
        assertThat(checklist)
                .contains("离线 fallback")
                .contains("待补传");
        assertThat(audit)
                .contains("离线 fallback")
                .contains("待补传");
    }

    @Test
    void readinessFallbackPackageCanBeReplayedIntoReadiness() throws IOException {
        Path scriptPath = Path.of("scripts", "replay-readiness-evidence.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string[]]$ManifestPath")
                .contains("[string]$ManifestDirectory")
                .contains("[string]$BaseUrl")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[switch]$ValidateOnly")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("function Get-ReadinessReplayHeaders")
                .contains("function Validate-ReadinessReplayManifest")
                .contains("ConvertFrom-Json")
                .contains("Register-ReadinessEvidence")
                .contains("/api/auth/login")
                .contains("uploadStatus")
                .contains("UPLOADED")
                .contains("uploadedAt")
                .contains("Set-Content -LiteralPath $manifestPath")
                .contains("Readiness evidence replay");

        assertThat(deployment)
                .contains(".\\scripts\\replay-readiness-evidence.ps1")
                .contains("-ValidateOnly")
                .contains("补传");
        assertThat(checklist)
                .contains(".\\scripts\\replay-readiness-evidence.ps1")
                .contains("补传");
        assertThat(audit)
                .contains(".\\scripts\\replay-readiness-evidence.ps1")
                .contains("补传");
    }

    @Test
    void existingReadinessItemsCanBeBackfilledWithManualEvidence() throws IOException {
        Path scriptPath = Path.of("scripts", "register-readiness-item-result.ps1");
        String localRuntime = Files.readString(Path.of("docs", "local-runtime-integration.md"), StandardCharsets.UTF_8);
        String missing = Files.readString(Path.of("docs", "WHAT_IS_MISSING.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[long]$ReadinessRunId")
                .contains("[string]$ItemCode")
                .contains("[string]$Status")
                .contains("[string]$EvidenceSummary")
                .contains("[string]$EvidenceRequestUri")
                .contains("[string]$EvidenceDetailPath")
                .contains("[string]$EvidenceDetail")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("Get-ReadinessItemByCode")
                .contains("Register-ReadinessEvidenceWithOfflineFallback")
                .contains("/api/auth/login")
                .contains("FINANCE_LEDGER")
                .contains("PERIOD_LOCK")
                .contains("INVENTORY_FINANCE_RECONCILIATION")
                .contains("INITIAL_IMPORT")
                .contains("BACKUP_ROLLBACK")
                .contains("Readiness item evidence registration");

        assertThat(localRuntime)
                .contains(".\\scripts\\register-readiness-item-result.ps1")
                .contains("FINANCE_LEDGER");
        assertThat(missing)
                .contains(".\\scripts\\register-readiness-item-result.ps1")
                .contains("BACKUP_ROLLBACK");
    }

    @Test
    void uploadedReadinessEvidenceCanBeVerifiedAgainstSystemRun() throws IOException {
        Path scriptPath = Path.of("scripts", "verify-readiness-evidence-upload.ps1");
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string[]]$ManifestPath")
                .contains("[string]$ManifestDirectory")
                .contains("[string]$BaseUrl")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$ReadinessRunId")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("function Get-ReadinessUploadManifestPaths")
                .contains("function Get-ReadinessUploadHeaders")
                .contains("function Assert-UploadedReadinessManifest")
                .contains("function Get-ReadinessRunDetail")
                .contains("function Find-ReadinessItem")
                .contains("function Find-ReadinessEvidence")
                .contains("/api/auth/login")
                .contains("/api/system/readiness/runs/")
                .contains("uploadedItemId")
                .contains("uploadedEvidenceId")
                .contains("uploadedAttachmentId")
                .contains("uploadedReadinessStatus")
                .contains("attachmentBusinessId")
                .contains("UPLOADED")
                .contains("Readiness evidence upload verification");

        assertThat(deployment)
                .contains(".\\scripts\\verify-readiness-evidence-upload.ps1")
                .contains("系统 readiness 证据对账");
        assertThat(checklist)
                .contains(".\\scripts\\verify-readiness-evidence-upload.ps1")
                .contains("系统 readiness 证据对账");
        assertThat(audit)
                .contains(".\\scripts\\verify-readiness-evidence-upload.ps1")
                .contains("系统 readiness 证据对账");
    }

    private static void assertHelperCoversReadinessApi() throws IOException {
        Path helperPath = Path.of("scripts", "readiness-evidence.ps1");

        assertThat(helperPath).exists().isRegularFile();

        String helper = Files.readString(helperPath, StandardCharsets.UTF_8);
        assertThat(helper)
                .contains("function Register-ReadinessEvidence")
                .contains("/api/system/readiness/runs/$ReadinessRunId")
                .contains("/api/system/readiness/runs/$ReadinessRunId/items")
                .contains("/api/system/readiness/items/$itemId/evidence")
                .contains("/api/system/readiness/items/$itemId/result")
                .contains("ConvertTo-Json");
    }

    private static void assertScriptRegistersReadinessEvidence(String scriptFile, String... itemCodes) throws IOException {
        String script = Files.readString(Path.of("scripts", scriptFile), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[long]$ReadinessRunId")
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("Register-ReadinessEvidence")
                .contains("Readiness evidence registration");
        for (String itemCode : itemCodes) {
            assertThat(script).contains(itemCode);
        }
    }

    private static void assertScriptUsesReadinessOfflineFallback(String scriptFile) throws IOException {
        String script = Files.readString(Path.of("scripts", scriptFile), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("Register-ReadinessEvidenceWithOfflineFallback")
                .contains("registration failure");
    }
}

package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PreproductionFullAcceptanceScriptConfigurationTest {

    @Test
    void fullAcceptanceScriptOrchestratesReadinessRunAndBusinessEvidence() throws IOException {
        Path scriptPath = Path.of("scripts", "preprod-full-acceptance.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$EnvFile")
                .contains("[string]$BaseUrl")
                .contains("[string]$OutputPath")
                .contains("[string]$EvidenceDirectory")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$ReadinessRunId")
                .contains("[long]$WarehouseId")
                .contains("[long]$MaterialWarehouseId")
                .contains("[long]$FinishedWarehouseId")
                .contains("[string]$BusinessDate")
                .contains("[switch]$ContinueAfterFailure")
                .contains("[switch]$RollbackAfterSuccess")
                .contains("[switch]$DisableCreatedMasterData")
                .contains("preprod-acceptance.ps1")
                .contains("business-smoke.ps1")
                .contains("purchase-to-payment-acceptance.ps1")
                .contains("sales-to-cash-acceptance.ps1")
                .contains("production-manufacturing-acceptance.ps1")
                .contains("-CreateReadinessRun")
                .contains("function Get-ReadinessRunIdFromReport")
                .contains("Readiness run ID:\\s*(\\d+)")
                .contains("-ReadinessRunId")
                .contains("Go / No-Go")
                .contains("``$($result.ScriptFile)``")
                .contains("``$($result.OutputPath)``")
                .doesNotContain("| `$($result.ScriptFile)` |")
                .doesNotContain("| `$($result.OutputPath)` |")
                .contains("NO-GO")
                .contains("Set-Content -LiteralPath $OutputPath");
    }

    @Test
    void fullAcceptanceScriptRegistersGoNoGoSummaryIntoReadiness() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains(". (Join-Path $PSScriptRoot \"readiness-evidence.ps1\")")
                .contains("function Get-ReadinessHeaders")
                .contains("/api/auth/login")
                .contains("Register-ReadinessEvidence")
                .contains("PREPROD_FULL_ACCEPTANCE")
                .contains("一键预生产验收总判定")
                .contains("Readiness full acceptance evidence registration")
                .contains("Readiness evidence ID")
                .contains("Readiness attachment ID");

        assertThat(deployment)
                .contains("PREPROD_FULL_ACCEPTANCE")
                .contains("总判定");
        assertThat(checklist)
                .contains("PREPROD_FULL_ACCEPTANCE")
                .contains("总判定");
        assertThat(audit)
                .contains("PREPROD_FULL_ACCEPTANCE")
                .contains("总判定");
    }

    @Test
    void fullAcceptanceScriptRunsPreflightBeforeWritableBusinessAcceptance() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Invoke-PreflightValidation")
                .contains("function Assert-ActiveWarehouse")
                .contains("function Assert-OpenBusinessPeriod")
                .contains("function Assert-RequiredPermissions")
                .contains("/api/masterdata/warehouses/")
                .contains("/api/finance/periods?year=")
                .contains("ACTIVE")
                .contains("OPEN")
                .contains("system:readiness:manage")
                .contains("system:attachment:manage")
                .contains("Preflight validation")
                .contains("Preflight validation failure");

        assertThat(script.indexOf("Invoke-PreflightValidation"))
                .isLessThan(script.indexOf("\"Business smoke\" \"business-smoke.ps1\""));

        assertThat(deployment)
                .contains("前置校验")
                .contains("WarehouseId")
                .contains("BusinessDate")
                .contains("OPEN");
        assertThat(checklist)
                .contains("前置校验")
                .contains("OPEN");
        assertThat(audit)
                .contains("前置校验")
                .contains("OPEN");
    }

    @Test
    void fullAcceptanceScriptSupportsPreflightOnlyDiagnosticMode() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("[switch]$PreflightOnly")
                .contains("function Add-PreflightDiagnosticGuidance")
                .contains("Diagnostic preflight mode")
                .contains("No business data was written.")
                .contains("If the permission list is SKIPPED, rerun with -Username and -Password")
                .contains("if ($runRemaining -and $PreflightOnly)")
                .contains("Skipped because -PreflightOnly was specified; diagnostic mode stops before business scripts and writes no business data.")
                .contains("if ($goNoGoVerdict -eq \"NO-GO\" -and -not $PreflightOnly)");

        assertThat(script.indexOf("if ($runRemaining -and $PreflightOnly)"))
                .isGreaterThan(script.indexOf("Invoke-PreflightValidation"))
                .isLessThan(script.indexOf("\"Business smoke\" \"business-smoke.ps1\""));

        assertThat(deployment)
                .contains("-PreflightOnly")
                .contains("诊断模式")
                .contains("不写入业务数据");
        assertThat(checklist)
                .contains("-PreflightOnly")
                .contains("诊断模式");
        assertThat(audit)
                .contains("-PreflightOnly")
                .contains("诊断模式");
    }

    @Test
    void fullAcceptanceScriptReportsParameterSelfCheckAndSanitizedCommand() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function New-FullAcceptanceInvocationArguments")
                .contains("function Get-RedactedFullAcceptanceCommandLine")
                .contains("function Add-ParameterSelfCheckSection")
                .contains("Parameter self-check")
                .contains("Sanitized command")
                .contains("Fix examples")
                .contains("Missing authentication")
                .contains("Missing WarehouseId")
                .contains("Format-CommandLine \"preprod-full-acceptance.ps1\"")
                .contains("Add-ParameterSelfCheckSection");

        assertThat(script.indexOf("Add-ParameterSelfCheckSection"))
                .isLessThan(script.indexOf("Provide -AccessToken or both -Username and -Password."));

        assertThat(deployment)
                .contains("参数自检")
                .contains("脱敏命令")
                .contains("Sanitized command");
        assertThat(checklist)
                .contains("参数自检")
                .contains("脱敏命令");
        assertThat(audit)
                .contains("参数自检")
                .contains("脱敏命令");
    }

    @Test
    void fullAcceptanceScriptBuildsFailureTriageIndex() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Add-FailureTriageIndex")
                .contains("Failure triage index")
                .contains("| Priority | Step | Status | Evidence | Reason | Recommended rerun command |")
                .contains("Recommended rerun command")
                .contains("P0")
                .contains("P1")
                .contains("Where-Object { $_.Status -ne \"PASSED\" }")
                .contains("Add-FailureTriageIndex")
                .contains("``$evidence``")
                .contains("``$command``")
                .contains("``$OutputPath``")
                .doesNotContain("| `$evidence` |")
                .doesNotContain("| `$command` |")
                .doesNotContain("| `$OutputPath` |");

        assertThat(script.indexOf("Add-FailureTriageIndex"))
                .isLessThan(script.indexOf("Add-GoNoGoSection"));

        assertThat(deployment)
                .contains("Failure triage index")
                .contains("失败定位索引")
                .contains("复跑命令");
        assertThat(checklist)
                .contains("失败定位索引")
                .contains("复跑命令");
        assertThat(audit)
                .contains("失败定位索引")
                .contains("复跑命令");
    }

    @Test
    void fullAcceptanceScriptWritesEvidenceIndexManifest() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-full-acceptance.ps1"), StandardCharsets.UTF_8);
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("function Save-EvidenceIndexManifest")
                .contains("function Add-EvidenceIndexSection")
                .contains("function Test-EvidenceIndexReportExists")
                .contains("evidence-index.json")
                .contains("fallbackPackages")
                .contains("stepResults")
                .contains("failureTriageSection")
                .contains("goNoGoVerdict")
                .contains("ReadinessRunId")
                .contains("replay-readiness-evidence.ps1")
                .contains("[System.IO.Path]::GetFullPath($OutputPath)")
                .contains("return [string]::Equals($fullPath, $fullOutputPath")
                .contains("Set-Content -LiteralPath $evidenceIndexPath")
                .contains("Evidence index");

        assertThat(script.lastIndexOf("Add-EvidenceIndexSection"))
                .isGreaterThan(script.indexOf("Register-FullAcceptanceEvidence -Summary $summary"))
                .isLessThan(script.indexOf("Set-Content -LiteralPath $OutputPath"));

        assertThat(deployment)
                .contains("evidence-index.json")
                .contains("证据索引");
        assertThat(checklist)
                .contains("evidence-index.json")
                .contains("证据索引");
        assertThat(audit)
                .contains("evidence-index.json")
                .contains("证据索引");
    }

    @Test
    void releaseDocumentsReferenceFullAcceptanceScript() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\preprod-full-acceptance.ps1")
                .contains("一键预生产验收");

        assertThat(checklist)
                .contains(".\\scripts\\preprod-full-acceptance.ps1")
                .contains("Go / No-Go");

        assertThat(audit)
                .contains(".\\scripts\\preprod-full-acceptance.ps1")
                .contains("一键预生产验收");
    }
}

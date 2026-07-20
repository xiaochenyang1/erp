package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessSmokeScriptConfigurationTest {

    @Test
    void businessSmokeScriptCoversCoreReadOnlyApiEntrypoints() throws IOException {
        Path scriptPath = Path.of("scripts", "business-smoke.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$BaseUrl")
                .contains("[string]$OutputPath")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[switch]$AllowFailures")
                .contains("/api/auth/login")
                .contains("ConvertTo-Json")
                .contains("accessToken")
                .contains("Authorization")
                .contains("Business smoke evidence")
                .contains("/api/system/profile")
                .contains("/api/masterdata/products")
                .contains("/api/masterdata/customers")
                .contains("/api/masterdata/suppliers")
                .contains("/api/masterdata/warehouses")
                .contains("/api/purchase/orders")
                .contains("/api/purchase/receipts")
                .contains("/api/sales/orders")
                .contains("/api/sales/deliveries")
                .contains("/api/inventory/balances")
                .contains("/api/inventory/transactions")
                .contains("/api/finance/receivables")
                .contains("/api/finance/payables")
                .contains("/api/finance/vouchers")
                .contains("/api/finance/periods")
                .contains("/api/production/boms")
                .contains("/api/production/orders")
                .contains("/api/workflow/tasks")
                .contains("/api/import/jobs")
                .contains("/api/reports/purchase-orders")
                .contains("/api/reports/sales-orders")
                .contains("Set-Content -LiteralPath $OutputPath");
    }

    @Test
    void releaseDocumentsReferenceBusinessSmokeScript() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\business-smoke.ps1")
                .contains("业务只读冒烟");

        assertThat(checklist)
                .contains(".\\scripts\\business-smoke.ps1")
                .contains("业务只读冒烟");
    }
}

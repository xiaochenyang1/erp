package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionManufacturingAcceptanceScriptConfigurationTest {

    @Test
    void productionManufacturingScriptCoversManufacturingAcceptanceAndCompensationRollback() throws IOException {
        Path scriptPath = Path.of("scripts", "production-manufacturing-acceptance.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$BaseUrl")
                .contains("[string]$OutputPath")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$MaterialWarehouseId")
                .contains("[long]$FinishedWarehouseId")
                .contains("[string]$BusinessDate")
                .contains("[decimal]$PlannedQty")
                .contains("[decimal]$MaterialQtyPer")
                .contains("[decimal]$MaterialCostPrice")
                .contains("[decimal]$FinishedSalePrice")
                .contains("[decimal]$TaxRate")
                .contains("[decimal]$CompletionQty")
                .contains("[switch]$RollbackAfterSuccess")
                .contains("[switch]$SkipRollbackOnFailure")
                .contains("[switch]$DisableCreatedMasterData")
                .contains("/api/auth/login")
                .contains("ConvertTo-Json")
                .contains("accessToken")
                .contains("Authorization")
                .contains("Production manufacturing acceptance evidence")
                .contains("business compensation rollback")
                .contains("production completion reversal")
                .contains("/api/masterdata/suppliers")
                .contains("/api/masterdata/products")
                .contains("/api/masterdata/suppliers/{id}/disable")
                .contains("/api/masterdata/products/{id}/disable")
                .contains("/api/purchase/orders")
                .contains("/api/purchase/orders/{id}/submit")
                .contains("/api/purchase/orders/{id}/approve")
                .contains("/api/purchase/receipts")
                .contains("/api/purchase/receipts/{id}/post")
                .contains("/api/purchase/returns")
                .contains("/api/purchase/returns/{id}/post")
                .contains("/api/production/boms")
                .contains("/api/production/orders")
                .contains("/api/production/orders/{id}/release")
                .contains("/api/production/orders/{id}/issue")
                .contains("/api/production/orders/{id}/complete")
                .contains("/api/production/orders/{id}/reverse-completion")
                .contains("/api/production/orders/{id}/return-materials")
                .contains("Set-Content -LiteralPath $OutputPath");
    }

    @Test
    void releaseDocumentsReferenceProductionManufacturingAcceptanceScript() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);
        String audit = Files.readString(Path.of("docs", "production-readiness-audit.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\production-manufacturing-acceptance.ps1")
                .contains("生产制造补偿回滚")
                .contains("生产反完工");

        assertThat(checklist)
                .contains(".\\scripts\\production-manufacturing-acceptance.ps1")
                .contains("生产制造补偿回滚")
                .contains("生产反完工");

        assertThat(audit)
                .contains(".\\scripts\\production-manufacturing-acceptance.ps1")
                .contains("生产制造补偿回滚");
    }
}

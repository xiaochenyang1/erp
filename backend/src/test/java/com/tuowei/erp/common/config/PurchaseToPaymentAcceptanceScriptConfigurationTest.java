package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseToPaymentAcceptanceScriptConfigurationTest {

    @Test
    void purchaseToPaymentScriptCoversWritableAcceptanceAndCompensationRollback() throws IOException {
        Path scriptPath = Path.of("scripts", "purchase-to-payment-acceptance.ps1");

        assertThat(scriptPath).exists().isRegularFile();

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("[string]$BaseUrl")
                .contains("[string]$OutputPath")
                .contains("[string]$Username")
                .contains("[string]$Password")
                .contains("[string]$AccessToken")
                .contains("[long]$WarehouseId")
                .contains("[string]$BusinessDate")
                .contains("[decimal]$Quantity")
                .contains("[decimal]$Price")
                .contains("[decimal]$TaxRate")
                .contains("[switch]$RollbackAfterSuccess")
                .contains("[switch]$SkipRollbackOnFailure")
                .contains("[switch]$DisableCreatedMasterData")
                .contains("/api/auth/login")
                .contains("ConvertTo-Json")
                .contains("accessToken")
                .contains("Authorization")
                .contains("Purchase to payment acceptance evidence")
                .contains("business compensation rollback")
                .contains("/api/masterdata/suppliers")
                .contains("/api/masterdata/products")
                .contains("/api/masterdata/suppliers/{id}/disable")
                .contains("/api/masterdata/products/{id}/disable")
                .contains("/api/purchase/orders")
                .contains("/api/purchase/orders/{id}/submit")
                .contains("/api/purchase/orders/{id}/approve")
                .contains("/api/purchase/receipts")
                .contains("/api/purchase/receipts/{id}/post")
                .contains("/api/finance/payables")
                .contains("/api/finance/payments")
                .contains("/api/finance/payments/{id}/cancel")
                .contains("/api/purchase/returns")
                .contains("/api/purchase/returns/{id}/post")
                .contains("/api/purchase/orders/{id}/trace")
                .contains("Set-Content -LiteralPath $OutputPath");
    }

    @Test
    void releaseDocumentsReferencePurchaseToPaymentAcceptanceScript() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains(".\\scripts\\purchase-to-payment-acceptance.ps1")
                .contains("采购到付款补偿回滚");

        assertThat(checklist)
                .contains(".\\scripts\\purchase-to-payment-acceptance.ps1")
                .contains("采购到付款补偿回滚");
    }
}

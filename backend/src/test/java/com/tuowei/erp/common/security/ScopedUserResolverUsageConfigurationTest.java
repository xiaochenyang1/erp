package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedUserResolverUsageConfigurationTest {

    @Test
    void businessServicesUseSharedScopedUserResolver() throws IOException {
        for (String servicePath : servicesWithDataScopeLists()) {
            String source = Files.readString(Path.of(servicePath), StandardCharsets.UTF_8);
            assertThat(source)
                    .as(servicePath)
                    .contains("ScopedUserResolver")
                    .contains("scopedUserResolver.resolve")
                    .doesNotContain("loadScopedUserIds(");
        }
    }

    @Test
    void userMutationsEvictScopedUserResolverCache() throws IOException {
        String source = Files.readString(
                Path.of("src", "main", "java", "com", "tuowei", "erp", "system", "user", "service", "UserCommandService.java"),
                StandardCharsets.UTF_8
        );

        assertThat(source)
                .contains("ScopedUserResolver scopedUserResolver")
                .contains("scopedUserResolver.evictAll();");
    }

    private static List<String> servicesWithDataScopeLists() {
        return List.of(
                "src/main/java/com/tuowei/erp/finance/settlement/service/FinanceSettlementScopeSupport.java",
                "src/main/java/com/tuowei/erp/inventory/transfer/service/InventoryTransferService.java",
                "src/main/java/com/tuowei/erp/inventory/adjust/service/InventoryAdjustmentQueryService.java",
                "src/main/java/com/tuowei/erp/inventory/check/service/InventoryStockCheckQueryService.java",
                "src/main/java/com/tuowei/erp/production/order/service/ProductionOrderQueryService.java",
                "src/main/java/com/tuowei/erp/purchase/order/service/PurchaseOrderQueryService.java",
                "src/main/java/com/tuowei/erp/purchase/receipt/service/PurchaseReceiptQueryService.java",
                "src/main/java/com/tuowei/erp/purchase/returnorder/service/PurchaseReturnQueryService.java",
                "src/main/java/com/tuowei/erp/report/service/OrderReportQueryService.java",
                "src/main/java/com/tuowei/erp/report/service/BusinessTraceService.java",
                "src/main/java/com/tuowei/erp/sales/delivery/service/SalesDeliveryQueryService.java",
                "src/main/java/com/tuowei/erp/sales/order/service/SalesOrderQueryService.java",
                "src/main/java/com/tuowei/erp/sales/returnorder/service/SalesReturnQueryService.java"
        );
    }
}

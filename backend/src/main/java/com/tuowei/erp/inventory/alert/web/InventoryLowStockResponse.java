package com.tuowei.erp.inventory.alert.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryLowStockResponse(
        Long ruleId,
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal qtyOnHand,
        BigDecimal currentQuantity,
        BigDecimal minQty,
        BigDecimal minQuantity,
        BigDecimal shortageQty,
        BigDecimal maxQuantity,
        String alertType,
        LocalDateTime alertDate,
        String status,
        String remark
) {
    public InventoryLowStockResponse(
            Long ruleId,
            Long warehouseId,
            Long productId,
            BigDecimal qtyOnHand,
            BigDecimal minQty,
            BigDecimal shortageQty,
            String remark
    ) {
        this(
                ruleId,
                ruleId,
                warehouseId,
                null,
                productId,
                null,
                null,
                qtyOnHand,
                qtyOnHand,
                minQty,
                minQty,
                shortageQty,
                null,
                "LOW_STOCK",
                null,
                "ACTIVE",
                remark
        );
    }
}

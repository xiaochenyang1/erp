package com.tuowei.erp.inventory.check.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryStockCheckLineRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin("0.0000") BigDecimal actualQty,
        @NotNull @DecimalMin("0.0000") BigDecimal unitCost,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public InventoryStockCheckLineRequest(Long productId, BigDecimal actualQty, BigDecimal unitCost, String remark) {
        this(productId, actualQty, unitCost, null, null, null, remark);
    }
}

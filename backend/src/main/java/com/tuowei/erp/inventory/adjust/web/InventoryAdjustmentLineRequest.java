package com.tuowei.erp.inventory.adjust.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryAdjustmentLineRequest(
        @NotNull Long productId,
        @NotBlank String direction,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
        @NotNull @DecimalMin(value = "0.0000") BigDecimal unitCost,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String reason
) {
    public InventoryAdjustmentLineRequest(Long productId, String direction, BigDecimal qty, BigDecimal unitCost, String reason) {
        this(productId, direction, qty, unitCost, null, null, null, reason);
    }
}

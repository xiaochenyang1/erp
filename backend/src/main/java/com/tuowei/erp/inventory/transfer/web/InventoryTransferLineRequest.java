package com.tuowei.erp.inventory.transfer.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryTransferLineRequest(
        @NotNull Long productId,
        @NotNull BigDecimal qty,
        @NotNull BigDecimal unitCost,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
    public InventoryTransferLineRequest(Long productId, BigDecimal qty, BigDecimal unitCost, String remark) {
        this(productId, qty, unitCost, null, null, null, remark);
    }
}

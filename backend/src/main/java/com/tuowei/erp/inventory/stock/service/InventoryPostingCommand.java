package com.tuowei.erp.inventory.stock.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryPostingCommand(
        Long warehouseId,
        Long productId,
        String bizType,
        String bizNo,
        Long bizLineId,
        BigDecimal qty,
        BigDecimal amount,
        String remark,
        LocalDate bizDate,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate
) {
    public InventoryPostingCommand(
            Long warehouseId,
            Long productId,
            String bizType,
            String bizNo,
            Long bizLineId,
            BigDecimal qty,
            BigDecimal amount,
            String remark
    ) {
        this(warehouseId, productId, bizType, bizNo, bizLineId, qty, amount, remark, null, null, null, null);
    }

    public InventoryPostingCommand(
            Long warehouseId,
            Long productId,
            String bizType,
            String bizNo,
            Long bizLineId,
            BigDecimal qty,
            BigDecimal amount,
            String remark,
            LocalDate bizDate
    ) {
        this(warehouseId, productId, bizType, bizNo, bizLineId, qty, amount, remark, bizDate, null, null, null);
    }
}

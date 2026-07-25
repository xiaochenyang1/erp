package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryLotTraceResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String bizType,
        String bizNo,
        Long bizLineId,
        String direction,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal unitCost,
        LocalDateTime occurredTime,
        String remark,
        String documentRoute,
        String documentLabel
) {
}

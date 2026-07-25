package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long id,
        Long warehouseId,
        Long locationId,
        Long productId,
        String bizType,
        String bizNo,
        Long bizLineId,
        String direction,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal unitCost,
        LocalDateTime occurredTime,
        String remark
) {
}

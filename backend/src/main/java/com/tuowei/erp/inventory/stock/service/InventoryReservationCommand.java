package com.tuowei.erp.inventory.stock.service;

import java.math.BigDecimal;

public record InventoryReservationCommand(
        Long warehouseId,
        Long productId,
        String sourceType,
        Long sourceId,
        String sourceNo,
        Long sourceLineId,
        BigDecimal qty,
        String remark
) {
}

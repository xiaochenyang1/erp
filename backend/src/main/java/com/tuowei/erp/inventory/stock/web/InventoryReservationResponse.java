package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryReservationResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String sourceType,
        Long sourceId,
        String sourceNo,
        Long sourceLineId,
        BigDecimal reservedQty,
        BigDecimal releasedQty,
        BigDecimal remainingQty,
        String status,
        String remark,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}

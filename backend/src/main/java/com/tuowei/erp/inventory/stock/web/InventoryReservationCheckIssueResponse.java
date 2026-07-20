package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;

public record InventoryReservationCheckIssueResponse(
        String issueType,
        String severity,
        Long reservationId,
        Long warehouseId,
        Long productId,
        String sourceType,
        Long sourceId,
        String sourceNo,
        BigDecimal expectedQty,
        BigDecimal actualQty,
        String message
) {
}

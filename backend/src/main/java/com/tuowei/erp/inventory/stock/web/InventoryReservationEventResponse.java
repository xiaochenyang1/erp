package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryReservationEventResponse(
        Long id,
        Long reservationId,
        String eventType,
        BigDecimal eventQty,
        BigDecimal remainingQtyBefore,
        BigDecimal remainingQtyAfter,
        String reason,
        Long createdBy,
        LocalDateTime createdTime
) {
}

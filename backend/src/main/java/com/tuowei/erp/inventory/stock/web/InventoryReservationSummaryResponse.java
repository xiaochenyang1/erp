package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;

public record InventoryReservationSummaryResponse(
        Long warehouseId,
        Long productId,
        String sourceType,
        String status,
        BigDecimal reservedQty,
        BigDecimal releasedQty,
        BigDecimal remainingQty,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        long reservationCount
) {
}

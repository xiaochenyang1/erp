package com.tuowei.erp.inventory.stock.web;

import java.util.List;

public record InventoryReservationSourceResponse(
        String sourceType,
        Long sourceId,
        String sourceNo,
        List<InventoryReservationDetailResponse> reservations
) {
}

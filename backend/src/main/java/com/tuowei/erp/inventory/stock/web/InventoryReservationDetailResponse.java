package com.tuowei.erp.inventory.stock.web;

import java.util.List;

public record InventoryReservationDetailResponse(
        InventoryReservationResponse reservation,
        List<InventoryReservationEventResponse> events
) {
}

package com.tuowei.erp.inventory.replenishment.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryReplenishmentSuggestionUpdateRequest(
        Long supplierId,
        @NotNull @DecimalMin("0.0001") BigDecimal suggestedQty,
        LocalDate expectedArrivalDate,
        String remark
) {
}

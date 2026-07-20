package com.tuowei.erp.inventory.check.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InventoryStockCheckUpdateRequest(
        @Valid @NotEmpty List<InventoryStockCheckUpdateLineRequest> items
) {
}

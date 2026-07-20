package com.tuowei.erp.inventory.check.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record InventoryStockCheckCreateRequest(
        @NotNull Long warehouseId,
        @NotNull LocalDate checkDate,
        String remark,
        @Valid @NotEmpty List<InventoryStockCheckLineRequest> lines
) {
}

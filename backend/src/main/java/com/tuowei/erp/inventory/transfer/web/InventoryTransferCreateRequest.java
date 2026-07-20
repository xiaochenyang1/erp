package com.tuowei.erp.inventory.transfer.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record InventoryTransferCreateRequest(
        @NotNull Long fromWarehouseId,
        @NotNull Long toWarehouseId,
        @NotNull LocalDate transferDate,
        @NotEmpty List<@Valid InventoryTransferLineRequest> lines,
        String remark
) {}
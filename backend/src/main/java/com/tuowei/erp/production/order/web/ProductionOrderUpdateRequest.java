package com.tuowei.erp.production.order.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionOrderUpdateRequest(
        @NotNull Long finishedWarehouseId,
        @NotNull Long materialWarehouseId,
        @NotNull BigDecimal plannedQty,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedFinishDate,
        String remark
) {
}

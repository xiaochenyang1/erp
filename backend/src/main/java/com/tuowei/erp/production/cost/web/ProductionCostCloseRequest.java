package com.tuowei.erp.production.cost.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProductionCostCloseRequest(
        @NotNull(message = "businessDate不能为空") LocalDate businessDate,
        @Size(max = 255, message = "remark长度不能超过255") String remark
) {
}

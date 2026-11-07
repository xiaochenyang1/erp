package com.tuowei.erp.production.cost.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProductionCostEntryReversalRequest(
        @NotNull(message = "businessDate不能为空") LocalDate businessDate,
        @NotBlank(message = "reason不能为空")
        @Size(max = 255, message = "reason长度不能超过255") String reason
) {
}

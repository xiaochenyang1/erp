package com.tuowei.erp.production.cost.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCostEntryRequest(
        @NotBlank(message = "costType不能为空")
        @Pattern(regexp = "LABOR|OVERHEAD", message = "costType只能为LABOR或OVERHEAD") String costType,
        Long operationId,
        @NotNull(message = "businessDate不能为空") LocalDate businessDate,
        @NotNull(message = "amount不能为空")
        @DecimalMin(value = "0.01", message = "amount必须大于等于0.01") BigDecimal amount,
        @Size(max = 255, message = "remark长度不能超过255") String remark
) {
}

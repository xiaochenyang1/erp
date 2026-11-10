package com.tuowei.erp.production.cost.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCostEntryCorrectionRequest(
        @NotBlank(message = "costType不能为空")
        @Pattern(regexp = "LABOR|OVERHEAD", message = "costType只能为LABOR或OVERHEAD") String costType,
        Long operationId,
        @NotNull(message = "businessDate不能为空") LocalDate businessDate,
        @NotNull(message = "amount不能为空")
        @DecimalMin(value = "0.01", message = "amount必须大于等于0.01") BigDecimal amount,
        @NotBlank(message = "reason不能为空")
        @Size(max = 255, message = "reason长度不能超过255") String reason,
        @Size(max = 255, message = "remark长度不能超过255") String remark
) {
}

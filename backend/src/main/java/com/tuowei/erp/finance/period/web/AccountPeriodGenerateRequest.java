package com.tuowei.erp.finance.period.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AccountPeriodGenerateRequest(
        @NotNull(message = "年度不能为空")
        @Min(value = 2000, message = "年度不能早于2000")
        @Max(value = 2199, message = "年度不能晚于2199")
        Integer year
) {
}

package com.tuowei.erp.finance.budget.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BudgetLineRequest(
        @NotNull @Min(0) @Max(12) Integer periodMonth,
        Long deptId,
        @NotNull Long subjectId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal budgetAmount,
        String remark
) { }

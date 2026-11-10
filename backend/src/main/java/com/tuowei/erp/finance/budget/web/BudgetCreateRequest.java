package com.tuowei.erp.finance.budget.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BudgetCreateRequest(
        @NotNull @Min(2000) @Max(2100) Integer budgetYear,
        @NotBlank String budgetName,
        String controlPolicy,
        String remark,
        @Valid @NotEmpty List<BudgetLineRequest> lines
) { }

package com.tuowei.erp.finance.budget.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BudgetUpdateRequest(
        @NotBlank String budgetName,
        String controlPolicy,
        String remark,
        @Valid @NotEmpty List<BudgetLineRequest> lines
) { }

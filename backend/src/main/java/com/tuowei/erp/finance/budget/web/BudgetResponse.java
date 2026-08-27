package com.tuowei.erp.finance.budget.web;

import java.math.BigDecimal;
import java.util.List;

public record BudgetResponse(
        Long id,
        Integer budgetYear,
        String budgetName,
        String controlPolicy,
        String status,
        BigDecimal totalBudgetAmount,
        BigDecimal totalCommittedAmount,
        BigDecimal totalActualAmount,
        BigDecimal totalAvailableAmount,
        String remark,
        List<BudgetLineResponse> lines
) { }

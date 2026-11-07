package com.tuowei.erp.finance.budget.web;

import java.math.BigDecimal;

public record BudgetExecutionResponse(
        Integer budgetYear,
        Integer periodMonth,
        Long deptId,
        Long subjectId,
        BigDecimal budgetAmount,
        BigDecimal committedAmount,
        BigDecimal actualAmount,
        BigDecimal availableAmount,
        Long budgetId,
        Long budgetLineId,
        String controlPolicy,
        String periodSource,
        BigDecimal requestedAmount,
        BigDecimal projectedAvailableAmount,
        Boolean overrun
) {
    public BudgetExecutionResponse(
            Integer budgetYear,
            Integer periodMonth,
            Long deptId,
            Long subjectId,
            BigDecimal budgetAmount,
            BigDecimal committedAmount,
            BigDecimal actualAmount,
            BigDecimal availableAmount
    ) {
        this(budgetYear, periodMonth, deptId, subjectId, budgetAmount, committedAmount,
                actualAmount, availableAmount, null, null, null, "NONE", BigDecimal.ZERO,
                availableAmount, false);
    }
}

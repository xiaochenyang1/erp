package com.tuowei.erp.finance.budget.web;

import java.math.BigDecimal;

public record BudgetLineResponse(
        Long id,
        Integer periodMonth,
        Long deptId,
        Long subjectId,
        BigDecimal budgetAmount,
        BigDecimal committedAmount,
        BigDecimal actualAmount,
        BigDecimal availableAmount,
        String remark
) { }

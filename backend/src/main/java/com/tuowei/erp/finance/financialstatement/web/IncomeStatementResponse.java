package com.tuowei.erp.finance.financialstatement.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IncomeStatementResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        String formulaVersion,
        List<FinancialStatementLineResponse> revenueLines,
        List<FinancialStatementLineResponse> expenseLines,
        BigDecimal totalRevenue,
        BigDecimal totalExpense,
        BigDecimal netProfit,
        List<String> unclassifiedSubjectCodes,
        List<String> mappingErrors
) {
}

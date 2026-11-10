package com.tuowei.erp.finance.cashflow.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        String formulaVersion,
        List<CashFlowLineResponse> operatingLines,
        List<CashFlowLineResponse> investingLines,
        List<CashFlowLineResponse> financingLines,
        BigDecimal netOperatingCashFlow,
        BigDecimal netInvestingCashFlow,
        BigDecimal netFinancingCashFlow,
        BigDecimal netIncreaseInCash,
        BigDecimal openingCash,
        BigDecimal closingCash,
        BigDecimal reconciliationDifference,
        boolean reconciled,
        List<String> unmappedSubjectCodes,
        List<String> mappingErrors
) {
}

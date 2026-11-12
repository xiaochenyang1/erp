package com.tuowei.erp.finance.financialstatement.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BalanceSheetResponse(
        LocalDate asOfDate,
        String formulaVersion,
        List<FinancialStatementLineResponse> assetLines,
        List<FinancialStatementLineResponse> liabilityLines,
        List<FinancialStatementLineResponse> equityLines,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal liabilitiesAndEquity,
        BigDecimal difference,
        boolean balanced,
        List<String> unclassifiedSubjectCodes,
        List<String> mappingErrors
) {
}

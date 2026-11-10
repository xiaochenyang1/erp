package com.tuowei.erp.finance.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TrialBalanceResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<TrialBalanceRowResponse> rows,
        BigDecimal openingDebit,
        BigDecimal openingCredit,
        BigDecimal periodDebit,
        BigDecimal periodCredit,
        BigDecimal closingDebit,
        BigDecimal closingCredit,
        boolean openingBalanced,
        boolean periodBalanced,
        boolean closingBalanced
) {
}

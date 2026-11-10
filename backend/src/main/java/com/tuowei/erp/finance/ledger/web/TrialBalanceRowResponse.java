package com.tuowei.erp.finance.ledger.web;

import java.math.BigDecimal;

public record TrialBalanceRowResponse(
        String subjectCode,
        String subjectName,
        String subjectType,
        String balanceDirection,
        BigDecimal openingDebit,
        BigDecimal openingCredit,
        BigDecimal periodDebit,
        BigDecimal periodCredit,
        BigDecimal closingDebit,
        BigDecimal closingCredit
) {
}

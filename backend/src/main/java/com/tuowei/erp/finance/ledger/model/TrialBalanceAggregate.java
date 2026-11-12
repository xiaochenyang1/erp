package com.tuowei.erp.finance.ledger.model;

import java.math.BigDecimal;

public record TrialBalanceAggregate(
        String subjectCode,
        String subjectName,
        String subjectType,
        String balanceDirection,
        BigDecimal openingDebit,
        BigDecimal openingCredit,
        BigDecimal periodDebit,
        BigDecimal periodCredit
) {
}

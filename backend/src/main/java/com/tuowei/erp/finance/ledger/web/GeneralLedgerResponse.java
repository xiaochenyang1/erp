package com.tuowei.erp.finance.ledger.web;

import java.math.BigDecimal;

public record GeneralLedgerResponse(
        String subjectCode,
        String subjectName,
        BigDecimal debitAmount,
        BigDecimal creditAmount
) {
}

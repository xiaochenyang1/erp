package com.tuowei.erp.finance.ledger.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DetailLedgerResponse(
        Long id,
        Long voucherId,
        LocalDate bizDate,
        Integer lineNo,
        String subjectCode,
        String subjectName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String summary
) {
}

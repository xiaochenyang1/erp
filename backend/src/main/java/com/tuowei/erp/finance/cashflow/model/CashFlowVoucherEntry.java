package com.tuowei.erp.finance.cashflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowVoucherEntry(
        Long voucherId,
        LocalDate bizDate,
        Integer lineNo,
        String subjectCode,
        String subjectName,
        BigDecimal debitAmount,
        BigDecimal creditAmount
) {
}

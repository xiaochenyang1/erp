package com.tuowei.erp.finance.voucher.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherEntryResponse(
        Long id,
        Long voucherId,
        LocalDate bizDate,
        Integer lineNo,
        Long subjectId,
        String subjectCode,
        String subjectName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String summary
) {
}

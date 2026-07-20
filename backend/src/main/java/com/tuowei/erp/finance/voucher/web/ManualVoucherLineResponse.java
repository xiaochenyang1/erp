package com.tuowei.erp.finance.voucher.web;

import java.math.BigDecimal;

public record ManualVoucherLineResponse(
        Long id,
        Integer lineNo,
        Long subjectId,
        String subjectCode,
        String subjectName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String summary
) {
}

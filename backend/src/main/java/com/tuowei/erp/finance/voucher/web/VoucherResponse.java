package com.tuowei.erp.finance.voucher.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherResponse(
        Long id,
        String voucherNo,
        String sourceType,
        Long sourceId,
        String sourceNo,
        LocalDate bizDate,
        BigDecimal amount,
        String status,
        ExpenseSourceSummary expenseSource,
        String remark
) {
    public record ExpenseSourceSummary(
            Long expenseId,
            String expenseNo,
            LocalDate expenseDate,
            String status,
            BigDecimal amount
    ) {
    }
}

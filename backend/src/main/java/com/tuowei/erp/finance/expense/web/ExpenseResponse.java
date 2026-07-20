package com.tuowei.erp.finance.expense.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        String expenseNo,
        LocalDate expenseDate,
        Long subjectId,
        Long paymentSubjectId,
        BigDecimal amount,
        String status,
        Long voucherId,
        String voucherNo,
        String voucherStatus,
        BigDecimal voucherAmount,
        Long voucherEntryCount,
        Boolean voucherBalanced,
        Boolean amountMatched,
        Long reversalVoucherId,
        String reversalVoucherNo,
        String reversalVoucherStatus,
        BigDecimal reversalVoucherAmount,
        Long reversalVoucherEntryCount,
        Boolean reversalVoucherBalanced,
        Boolean reversalAmountMatched,
        Boolean reversed,
        String remark
) {
}

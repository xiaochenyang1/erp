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
        String remark,
        Long deptId,
        Long budgetLineId,
        String budgetState,
        Integer budgetOverrunFlag
) {
    /** Compatibility constructor for callers that predate budget dimensions. */
    public ExpenseResponse(
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
        this(id, expenseNo, expenseDate, subjectId, paymentSubjectId, amount, status,
                voucherId, voucherNo, voucherStatus, voucherAmount, voucherEntryCount,
                voucherBalanced, amountMatched, reversalVoucherId, reversalVoucherNo,
                reversalVoucherStatus, reversalVoucherAmount, reversalVoucherEntryCount,
                reversalVoucherBalanced, reversalAmountMatched, reversed, remark,
                null, null, null, 0);
    }
}

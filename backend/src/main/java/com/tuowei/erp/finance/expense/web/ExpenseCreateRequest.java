package com.tuowei.erp.finance.expense.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCreateRequest(
        @NotNull(message = "expenseDate不能为空") LocalDate expenseDate,
        @NotNull(message = "subjectId不能为空") Long subjectId,
        @NotNull(message = "paymentSubjectId不能为空") Long paymentSubjectId,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "费用金额必须大于0") BigDecimal amount,
        String remark
) {
}

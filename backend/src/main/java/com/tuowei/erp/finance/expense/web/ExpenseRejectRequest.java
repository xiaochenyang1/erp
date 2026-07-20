package com.tuowei.erp.finance.expense.web;

import jakarta.validation.constraints.NotBlank;

public record ExpenseRejectRequest(
        @NotBlank(message = "reason不能为空") String reason
) {
}

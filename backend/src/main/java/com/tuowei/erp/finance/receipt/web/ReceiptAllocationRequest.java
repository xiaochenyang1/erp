package com.tuowei.erp.finance.receipt.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReceiptAllocationRequest(
        @NotNull(message = "receivableId不能为空") Long receivableId,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "核销金额必须大于0") BigDecimal amount
) {
}

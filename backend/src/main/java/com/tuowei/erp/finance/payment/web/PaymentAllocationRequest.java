package com.tuowei.erp.finance.payment.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        @NotNull(message = "payableId不能为空") Long payableId,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "核销金额必须大于0") BigDecimal amount
) {
}

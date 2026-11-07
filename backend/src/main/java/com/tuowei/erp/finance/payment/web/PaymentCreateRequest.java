package com.tuowei.erp.finance.payment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentCreateRequest(
        @NotNull(message = "supplierId不能为空") Long supplierId,
        @NotNull(message = "paymentDate不能为空") LocalDate paymentDate,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "付款金额必须大于0") BigDecimal amount,
        String currencyCode, BigDecimal exchangeRate,
        String remark,
        @Valid @NotEmpty(message = "allocations不能为空") List<PaymentAllocationRequest> allocations
) {
    /**
     * Backward-compatible constructor for base-currency payments.
     * A null currency and exchange rate are resolved as the account book's
     * base currency by the settlement command service.
     */
    public PaymentCreateRequest(
            Long supplierId,
            LocalDate paymentDate,
            BigDecimal amount,
            String remark,
            List<PaymentAllocationRequest> allocations
    ) {
        this(supplierId, paymentDate, amount, null, null, remark, allocations);
    }
}

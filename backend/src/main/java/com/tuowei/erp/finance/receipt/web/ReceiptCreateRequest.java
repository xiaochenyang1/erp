package com.tuowei.erp.finance.receipt.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptCreateRequest(
        @NotNull(message = "customerId不能为空") Long customerId,
        @NotNull(message = "receiptDate不能为空") LocalDate receiptDate,
        @NotNull(message = "amount不能为空") @DecimalMin(value = "0.01", message = "收款金额必须大于0") BigDecimal amount,
        String remark,
        @Valid @NotEmpty(message = "allocations不能为空") List<ReceiptAllocationRequest> allocations
) {
}

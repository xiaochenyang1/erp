package com.tuowei.erp.finance.receipt.web;

import java.math.BigDecimal;

public record ReceiptAllocationResponse(
        Long id,
        Long receivableId,
        BigDecimal amount
) {
}

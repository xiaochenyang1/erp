package com.tuowei.erp.finance.payment.web;

import java.math.BigDecimal;

public record PaymentAllocationResponse(
        Long id,
        Long payableId,
        BigDecimal amount
) {
}

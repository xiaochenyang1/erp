package com.tuowei.erp.finance.payment.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentResponse(
        Long id,
        String paymentNo,
        Long supplierId,
        LocalDate paymentDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<PaymentAllocationResponse> allocations
) {
}

package com.tuowei.erp.finance.receipt.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
        Long id,
        String receiptNo,
        Long customerId,
        LocalDate receiptDate,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String remark,
        String cancelReason,
        Long cancelledBy,
        LocalDateTime cancelledTime,
        List<ReceiptAllocationResponse> allocations
) {
}

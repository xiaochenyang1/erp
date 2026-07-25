package com.tuowei.erp.finance.receivable.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReceivableResponse(
        Long id,
        String receivableNo,
        Long customerId,
        String customerName,
        LocalDate bizDate,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String direction,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        String remark
) {
}

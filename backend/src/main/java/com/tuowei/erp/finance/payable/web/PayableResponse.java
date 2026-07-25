package com.tuowei.erp.finance.payable.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayableResponse(
        Long id,
        String payableNo,
        Long supplierId,
        String supplierName,
        LocalDate bizDate,
        LocalDate dueDate,
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

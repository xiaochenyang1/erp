package com.tuowei.erp.finance.receivable.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivableResponse(
        Long id,
        String receivableNo,
        Long customerId,
        LocalDate bizDate,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String direction,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status,
        String remark
) {
}

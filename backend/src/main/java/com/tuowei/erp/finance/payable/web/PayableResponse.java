package com.tuowei.erp.finance.payable.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayableResponse(
        Long id,
        String payableNo,
        Long supplierId,
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

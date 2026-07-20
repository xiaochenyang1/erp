package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderReportResponse(
        Long id,
        String bizNo,
        Long partnerId,
        LocalDate bizDate,
        String status,
        String approvalStatus,
        String fulfillmentStatus,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount
) {
}

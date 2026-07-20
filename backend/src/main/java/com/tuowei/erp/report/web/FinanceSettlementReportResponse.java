package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceSettlementReportResponse(
        Long id,
        String direction,
        String bizNo,
        Long partnerId,
        LocalDate bizDate,
        String sourceType,
        String sourceNo,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status
) {
}

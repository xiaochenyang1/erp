package com.tuowei.erp.finance.margin.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GrossMarginSummaryResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal salesAmount,
        BigDecimal costAmount,
        BigDecimal grossMargin,
        BigDecimal marginRate,
        List<GrossMarginLineResponse> lines
) {
}

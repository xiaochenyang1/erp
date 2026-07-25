package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceAgingOpenItemResponse(
        String side,
        Long id,
        String docNo,
        Long partnerId,
        String partnerName,
        LocalDate bizDate,
        LocalDate dueDate,
        long agingDays,
        String bucketCode,
        BigDecimal remainingAmount,
        String status
) {
}

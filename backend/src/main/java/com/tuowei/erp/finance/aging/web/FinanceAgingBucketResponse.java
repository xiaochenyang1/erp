package com.tuowei.erp.finance.aging.web;

import java.math.BigDecimal;

public record FinanceAgingBucketResponse(
        String code,
        String label,
        int minDaysInclusive,
        Integer maxDaysInclusive,
        long count,
        BigDecimal amount
) {
}

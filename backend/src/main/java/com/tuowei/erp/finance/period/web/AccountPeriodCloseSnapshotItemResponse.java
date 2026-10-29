package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;

public record AccountPeriodCloseSnapshotItemResponse(
        String code,
        String title,
        String category,
        boolean passed,
        String message,
        BigDecimal metric
) {
}

package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;

public record AccountPeriodCloseIssueResponse(
        String type,
        String message,
        BigDecimal amount
) {
}

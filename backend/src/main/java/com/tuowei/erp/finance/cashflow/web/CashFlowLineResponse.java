package com.tuowei.erp.finance.cashflow.web;

import java.math.BigDecimal;

public record CashFlowLineResponse(
        String itemCode,
        String itemName,
        String activityType,
        BigDecimal amount
) {
}

package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;

/**
 * 结账向导固定检查项：无论是否发现问题都会返回，便于前端 checklist 展示。
 */
public record AccountPeriodCloseCheckItemResponse(
        String code,
        String title,
        String category,
        boolean passed,
        String message,
        BigDecimal metric
) {
}

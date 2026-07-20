package com.tuowei.erp.finance.margin.web;

import java.math.BigDecimal;

public record GrossMarginLineResponse(
        Long productId,
        String productCode,
        String productName,
        BigDecimal salesQty,
        BigDecimal salesAmount,
        BigDecimal costAmount,
        BigDecimal grossMargin,
        BigDecimal marginRate
) {
}

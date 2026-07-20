package com.tuowei.erp.sales.quote.web;

import java.math.BigDecimal;

public record SalesQuoteLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        String remark
) {
}

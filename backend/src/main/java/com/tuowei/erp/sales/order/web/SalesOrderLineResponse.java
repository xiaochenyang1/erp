package com.tuowei.erp.sales.order.web;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal deliveredQty,
        String remark
) {
}

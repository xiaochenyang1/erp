package com.tuowei.erp.sales.order.web;

import java.math.BigDecimal;

public record SalesOrderLineResponse(
        Long id,
        Integer lineNo,
        Long contractLineId,
        Long productId,
        BigDecimal qty,
        BigDecimal auxQty,
        String auxUnitName,
        BigDecimal conversionFactor,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal deliveredQty,
        String remark
) {
    public SalesOrderLineResponse(Long id, Integer lineNo, Long productId, BigDecimal qty, BigDecimal auxQty,
                                  String auxUnitName, BigDecimal conversionFactor, BigDecimal price,
                                  BigDecimal taxRate, BigDecimal amount, BigDecimal taxAmount,
                                  BigDecimal deliveredQty, String remark) {
        this(id, lineNo, null, productId, qty, auxQty, auxUnitName, conversionFactor, price, taxRate,
                amount, taxAmount, deliveredQty, remark);
    }

    public SalesOrderLineResponse(
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
        this(id, lineNo, null, productId, qty, null, null, null, price, taxRate, amount, taxAmount, deliveredQty, remark);
    }
}

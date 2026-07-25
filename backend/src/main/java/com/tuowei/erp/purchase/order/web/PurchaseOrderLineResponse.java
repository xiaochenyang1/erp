package com.tuowei.erp.purchase.order.web;

import java.math.BigDecimal;

public record PurchaseOrderLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        BigDecimal auxQty,
        String auxUnitName,
        BigDecimal conversionFactor,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal receivedQty,
        Long sourceInquiryId,
        Long sourceInquiryLineId,
        String remark
) {
    public PurchaseOrderLineResponse(
            Long id,
            Integer lineNo,
            Long productId,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal taxRate,
            BigDecimal amount,
            BigDecimal taxAmount,
            BigDecimal receivedQty,
            Long sourceInquiryId,
            Long sourceInquiryLineId,
            String remark
    ) {
        this(id, lineNo, productId, qty, null, null, null, price, taxRate, amount, taxAmount, receivedQty, sourceInquiryId, sourceInquiryLineId, remark);
    }
}

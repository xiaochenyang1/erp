package com.tuowei.erp.purchase.order.web;

import java.math.BigDecimal;

public record PurchaseOrderLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal receivedQty,
        String remark
) {
}

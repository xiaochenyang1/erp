package com.tuowei.erp.purchase.order.web;

import java.math.BigDecimal;

public record PurchaseOrderExecutionInfo(
        BigDecimal orderedQty,
        BigDecimal receivedQty,
        BigDecimal remainingReceiptQty,
        String receiptStatus
) {
}

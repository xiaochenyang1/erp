package com.tuowei.erp.purchase.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;

public final class PurchaseReturnQuantities {

    private PurchaseReturnQuantities() {
    }

    public static ReceiptLineQuantities from(BigDecimal receiptQty, BigDecimal returnedQty) {
        BigDecimal normalizedReceiptQty = ScalePrecision.quantity(receiptQty);
        BigDecimal normalizedReturnedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(returnedQty));
        BigDecimal availableReturnQty = ScalePrecision.quantity(normalizedReceiptQty.subtract(normalizedReturnedQty));
        return new ReceiptLineQuantities(normalizedReceiptQty, normalizedReturnedQty, availableReturnQty);
    }

    public record ReceiptLineQuantities(
            BigDecimal receiptQty,
            BigDecimal returnedQty,
            BigDecimal availableReturnQty
    ) {
    }
}

package com.tuowei.erp.purchase.support;

import com.tuowei.erp.common.math.ScalePrecision;

import java.math.BigDecimal;

public final class PurchaseReceiptQuantities {

    private PurchaseReceiptQuantities() {
    }

    public static OrderLineQuantities from(BigDecimal orderQty, BigDecimal receivedQty) {
        BigDecimal normalizedOrderQty = ScalePrecision.quantity(orderQty);
        BigDecimal normalizedReceivedQty = ScalePrecision.safeQuantity(receivedQty);
        BigDecimal availableReceiptQty = ScalePrecision.quantity(normalizedOrderQty.subtract(normalizedReceivedQty));
        return new OrderLineQuantities(normalizedOrderQty, normalizedReceivedQty, availableReceiptQty);
    }

    public record OrderLineQuantities(
            BigDecimal orderQty,
            BigDecimal receivedQty,
            BigDecimal availableReceiptQty
    ) {
        public boolean hasReceived() {
            return receivedQty.compareTo(BigDecimal.ZERO) > 0;
        }

        public boolean fullyReceived() {
            return receivedQty.compareTo(orderQty) == 0;
        }

        public BigDecimal receivedQtyAfter(BigDecimal additionalReceiptQty) {
            return ScalePrecision.quantity(receivedQty.add(ScalePrecision.quantity(additionalReceiptQty)));
        }
    }
}

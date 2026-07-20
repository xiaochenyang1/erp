package com.tuowei.erp.purchase.support;

import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;

public record PurchaseReturnLineViewData(
        String productName,
        PurchaseReturnQuantities.ReceiptLineQuantities quantities
) {

    public static PurchaseReturnLineViewData from(PurchaseReceiptLineEntity receiptLine, ProductEntity product) {
        return new PurchaseReturnLineViewData(
                product.getProductName(),
                PurchaseReturnQuantities.from(receiptLine.getQty(), receiptLine.getReturnedQty())
        );
    }

    public void applyTo(PurchaseReturnLineEntity line) {
        line.setProductName(productName);
        line.setReceiptQty(quantities.receiptQty());
        line.setReturnedQty(quantities.returnedQty());
        line.setAvailableReturnQty(quantities.availableReturnQty());
    }
}

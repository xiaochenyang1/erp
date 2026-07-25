package com.tuowei.erp.purchase.order.service;

import java.util.List;

/**
 * Internal provenance carried when an inquiry is converted into a purchase order.
 * The line id list follows the purchase-order request line order.
 */
public record PurchaseOrderInquirySource(
        Long inquiryId,
        String inquiryNo,
        Long quoteId,
        List<Long> inquiryLineIds
) {
}

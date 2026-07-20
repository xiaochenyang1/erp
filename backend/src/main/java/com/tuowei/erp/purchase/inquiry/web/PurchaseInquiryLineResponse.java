package com.tuowei.erp.purchase.inquiry.web;

import java.math.BigDecimal;

public record PurchaseInquiryLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        String remark
) {
}

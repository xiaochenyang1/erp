package com.tuowei.erp.purchase.inquiry.web;

import java.math.BigDecimal;

public record PurchaseInquiryQuoteLineResponse(
        Long id,
        Long inquiryLineId,
        BigDecimal unitPrice,
        BigDecimal taxRate
) {
}

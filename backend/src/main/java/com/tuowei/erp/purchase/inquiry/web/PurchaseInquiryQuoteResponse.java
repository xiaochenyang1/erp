package com.tuowei.erp.purchase.inquiry.web;

import java.math.BigDecimal;

public record PurchaseInquiryQuoteResponse(
        Long id,
        Long supplierId,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        String status,
        String remark
) {
}

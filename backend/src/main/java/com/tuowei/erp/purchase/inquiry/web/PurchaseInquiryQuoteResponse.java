package com.tuowei.erp.purchase.inquiry.web;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseInquiryQuoteResponse(
        Long id,
        Long supplierId,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        String status,
        String remark,
        List<PurchaseInquiryQuoteLineResponse> lines
) {
}

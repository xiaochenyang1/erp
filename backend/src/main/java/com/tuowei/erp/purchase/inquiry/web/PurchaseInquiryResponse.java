package com.tuowei.erp.purchase.inquiry.web;

import java.time.LocalDate;
import java.util.List;

public record PurchaseInquiryResponse(
        Long id,
        String inquiryNo,
        LocalDate inquiryDate,
        String status,
        Long selectedSupplierId,
        Long selectedQuoteId,
        String title,
        String remark,
        List<PurchaseInquiryLineResponse> lines,
        List<PurchaseInquiryQuoteResponse> quotes
) {
}

package com.tuowei.erp.purchase.inquiry.web;

import jakarta.validation.constraints.NotNull;

public record PurchaseInquirySelectQuoteRequest(
        @NotNull(message = "quoteId不能为空") Long quoteId
) {
}

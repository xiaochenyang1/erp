package com.tuowei.erp.purchase.inquiry.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseInquiryQuoteRequest(
        @NotNull(message = "supplierId不能为空") Long supplierId,
        @DecimalMin(value = "0.00", message = "unitPrice不能小于0") BigDecimal unitPrice,
        @DecimalMin(value = "0.00", message = "taxRate不能小于0") BigDecimal taxRate,
        @Valid List<PurchaseInquiryQuoteLineRequest> lines,
        String remark
) {
    public PurchaseInquiryQuoteRequest(
            Long supplierId,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            String remark
    ) {
        this(supplierId, unitPrice, taxRate, null, remark);
    }
}

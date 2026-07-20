package com.tuowei.erp.purchase.inquiry.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInquiryQuoteRequest(
        @NotNull(message = "supplierId不能为空") Long supplierId,
        @NotNull(message = "unitPrice不能为空")
        @DecimalMin(value = "0.00", message = "unitPrice不能小于0") BigDecimal unitPrice,
        @DecimalMin(value = "0.00", message = "taxRate不能小于0") BigDecimal taxRate,
        String remark
) {
}

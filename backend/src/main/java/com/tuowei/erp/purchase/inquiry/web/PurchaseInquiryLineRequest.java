package com.tuowei.erp.purchase.inquiry.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInquiryLineRequest(
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "qty不能为空")
        @DecimalMin(value = "0.0001", message = "qty必须大于0") BigDecimal qty,
        String remark
) {
}

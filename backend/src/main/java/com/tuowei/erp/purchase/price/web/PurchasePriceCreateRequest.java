package com.tuowei.erp.purchase.price.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchasePriceCreateRequest(
        Long supplierId,
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "listPrice不能为空")
        @DecimalMin(value = "0.00", message = "listPrice不能小于0") BigDecimal listPrice,
        @NotNull(message = "maxPrice不能为空")
        @DecimalMin(value = "0.00", message = "maxPrice不能小于0") BigDecimal maxPrice,
        @NotNull(message = "effectiveFrom不能为空") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String remark
) {
}

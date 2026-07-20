package com.tuowei.erp.sales.price.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesPriceUpdateRequest(
        Long customerId,
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "listPrice不能为空")
        @DecimalMin(value = "0.00", message = "listPrice不能小于0") BigDecimal listPrice,
        @NotNull(message = "minPrice不能为空")
        @DecimalMin(value = "0.00", message = "minPrice不能小于0") BigDecimal minPrice,
        @NotNull(message = "effectiveFrom不能为空") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String remark
) {
}

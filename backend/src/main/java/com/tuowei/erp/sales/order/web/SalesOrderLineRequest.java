package com.tuowei.erp.sales.order.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalesOrderLineRequest(
        @NotNull(message = "productId不能为空") Long productId,
        @NotNull(message = "qty不能为空")
        @DecimalMin(value = "0.0001", message = "qty必须大于0") BigDecimal qty,
        @NotNull(message = "price不能为空")
        @DecimalMin(value = "0.00", message = "price不能小于0") BigDecimal price,
        @NotNull(message = "taxRate不能为空")
        @DecimalMin(value = "0.00", message = "taxRate不能小于0") BigDecimal taxRate,
        String remark
) {
}

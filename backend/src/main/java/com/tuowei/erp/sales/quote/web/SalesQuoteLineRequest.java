package com.tuowei.erp.sales.quote.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalesQuoteLineRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin("0.0001") BigDecimal qty,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        BigDecimal taxRate,
        String remark
) {
}

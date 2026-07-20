package com.tuowei.erp.sales.quote.web;

import jakarta.validation.constraints.NotNull;

public record SalesQuoteConvertRequest(
        @NotNull(message = "warehouseId不能为空") Long warehouseId
) {
}

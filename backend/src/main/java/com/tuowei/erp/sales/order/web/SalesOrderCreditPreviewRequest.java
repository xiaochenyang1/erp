package com.tuowei.erp.sales.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderCreditPreviewRequest(
        @NotNull(message = "customerId不能为空") Long customerId,
        LocalDate orderDate,
        String currencyCode,
        BigDecimal exchangeRate,
        @Valid List<SalesOrderLineRequest> lines
) {

    /** Compatibility constructor for the original preview payload. */
    public SalesOrderCreditPreviewRequest(Long customerId, List<SalesOrderLineRequest> lines) {
        this(customerId, null, null, null, lines);
    }
}

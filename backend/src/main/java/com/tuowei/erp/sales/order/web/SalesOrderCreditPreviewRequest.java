package com.tuowei.erp.sales.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SalesOrderCreditPreviewRequest(
        @NotNull(message = "customerId不能为空") Long customerId,
        @Valid List<SalesOrderLineRequest> lines
) {
}

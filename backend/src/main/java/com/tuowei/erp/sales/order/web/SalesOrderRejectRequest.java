package com.tuowei.erp.sales.order.web;

import jakarta.validation.constraints.NotBlank;

public record SalesOrderRejectRequest(
        @NotBlank(message = "reason不能为空") String reason
) {
}

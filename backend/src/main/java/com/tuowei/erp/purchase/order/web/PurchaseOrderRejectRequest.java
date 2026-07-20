package com.tuowei.erp.purchase.order.web;

import jakarta.validation.constraints.NotBlank;

public record PurchaseOrderRejectRequest(
        @NotBlank(message = "reason不能为空") String reason
) {
}

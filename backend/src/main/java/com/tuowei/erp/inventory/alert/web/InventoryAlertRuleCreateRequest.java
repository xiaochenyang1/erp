package com.tuowei.erp.inventory.alert.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryAlertRuleCreateRequest(
        @NotNull Long warehouseId,
        @NotNull Long productId,
        @NotNull @DecimalMin("0.0000") BigDecimal minQty,
        String remark
) {
}

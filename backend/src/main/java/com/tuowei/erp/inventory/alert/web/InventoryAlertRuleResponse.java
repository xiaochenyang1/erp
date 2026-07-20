package com.tuowei.erp.inventory.alert.web;

import java.math.BigDecimal;

public record InventoryAlertRuleResponse(
        Long id,
        Long warehouseId,
        Long productId,
        BigDecimal minQty,
        Boolean enabled,
        String remark
) {
}

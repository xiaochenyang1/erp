package com.tuowei.erp.inventory.alert.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryAlertRuleResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal minQty,
        Boolean enabled,
        String remark,
        LocalDateTime updatedTime
) {
}

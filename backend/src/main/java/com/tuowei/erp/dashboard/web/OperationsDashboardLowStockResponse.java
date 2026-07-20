package com.tuowei.erp.dashboard.web;

import java.math.BigDecimal;

public record OperationsDashboardLowStockResponse(
        Long ruleId,
        Long warehouseId,
        Long productId,
        BigDecimal qtyOnHand,
        BigDecimal minQty,
        BigDecimal shortageQty,
        String remark
) {
}

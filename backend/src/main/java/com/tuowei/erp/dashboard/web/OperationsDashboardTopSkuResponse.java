package com.tuowei.erp.dashboard.web;

import java.math.BigDecimal;

public record OperationsDashboardTopSkuResponse(
        Long productId,
        String productCode,
        String productName,
        String unitName,
        BigDecimal quantity,
        BigDecimal amount
) {
}

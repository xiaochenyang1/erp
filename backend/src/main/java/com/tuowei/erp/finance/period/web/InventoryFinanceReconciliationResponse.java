package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;

public record InventoryFinanceReconciliationResponse(
        Long periodId,
        String periodMonth,
        BigDecimal inventoryNetAmount,
        BigDecimal financeInventoryNetAmount,
        BigDecimal differenceAmount,
        boolean balanced
) {
}

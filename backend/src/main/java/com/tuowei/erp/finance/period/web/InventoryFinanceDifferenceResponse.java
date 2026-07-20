package com.tuowei.erp.finance.period.web;

import java.math.BigDecimal;

public record InventoryFinanceDifferenceResponse(
        String sourceKey,
        String sourceType,
        String sourceNo,
        BigDecimal inventoryAmount,
        BigDecimal financeAmount,
        BigDecimal differenceAmount,
        String differenceType
) {
}

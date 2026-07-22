package com.tuowei.erp.masterdata.product.web;

import java.math.BigDecimal;

public record ProductStockSummaryResponse(
        Long productId,
        int warehouseCount,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand
) {
}

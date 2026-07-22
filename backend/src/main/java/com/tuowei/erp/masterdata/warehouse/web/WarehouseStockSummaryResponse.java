package com.tuowei.erp.masterdata.warehouse.web;

import java.math.BigDecimal;

public record WarehouseStockSummaryResponse(
        Long warehouseId,
        int skuCount,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand
) {
}

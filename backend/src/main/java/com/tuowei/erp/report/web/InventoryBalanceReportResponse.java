package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryBalanceReportResponse(
        Long id,
        Long warehouseId,
        Long productId,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand,
        LocalDateTime updatedTime
) {
}

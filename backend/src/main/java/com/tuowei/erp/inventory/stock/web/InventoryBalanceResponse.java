package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryBalanceResponse(
        Long id,
        Long warehouseId,
        Long locationId,
        Long productId,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand,
        LocalDateTime updatedTime
) {
}

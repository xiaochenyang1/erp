package com.tuowei.erp.inventory.stock.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryLotBalanceResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        LocalDateTime firstInboundTime,
        BigDecimal qtyOnHand,
        BigDecimal qtyReserved,
        BigDecimal qtyAvailable,
        BigDecimal amountOnHand,
        LocalDateTime updatedTime
) {
}

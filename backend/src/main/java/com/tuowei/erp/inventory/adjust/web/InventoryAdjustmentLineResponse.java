package com.tuowei.erp.inventory.adjust.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryAdjustmentLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        String direction,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal amount,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        Long locationId,
        String serialNos,
        String reason,
        String remark
) {
}

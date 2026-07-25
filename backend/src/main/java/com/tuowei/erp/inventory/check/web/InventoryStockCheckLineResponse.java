package com.tuowei.erp.inventory.check.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryStockCheckLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        Long locationId,
        BigDecimal bookQty,
        BigDecimal actualQty,
        BigDecimal differenceQty,
        BigDecimal unitCost,
        BigDecimal differenceAmount,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String serialNos,
        String remark
) {
}

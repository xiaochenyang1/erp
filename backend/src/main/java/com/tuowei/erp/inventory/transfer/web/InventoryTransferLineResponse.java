package com.tuowei.erp.inventory.transfer.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryTransferLineResponse(
        Long id,
        Integer lineNo,
        Long productId,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal amount,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {}

package com.tuowei.erp.purchase.receipt.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseReceiptLineResponse(
        Long id,
        Integer lineNo,
        Long orderLineId,
        Long productId,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        Long locationId,
        String serialNos,
        String remark
) {
}

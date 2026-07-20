package com.tuowei.erp.purchase.returnorder.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseReturnLineResponse(
        Long id,
        Integer lineNo,
        Long receiptLineId,
        Long orderLineId,
        Long productId,
        String productName,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal receiptQty,
        BigDecimal returnedQty,
        BigDecimal availableReturnQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String remark
) {
}

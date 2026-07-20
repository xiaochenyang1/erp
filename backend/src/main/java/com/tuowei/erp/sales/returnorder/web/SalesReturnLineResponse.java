package com.tuowei.erp.sales.returnorder.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesReturnLineResponse(
        Long id,
        Integer lineNo,
        Long deliveryLineId,
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
        String remark
) {
}

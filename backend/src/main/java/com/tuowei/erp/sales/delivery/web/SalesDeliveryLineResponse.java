package com.tuowei.erp.sales.delivery.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesDeliveryLineResponse(
        Long id,
        Integer lineNo,
        Long orderLineId,
        Long productId,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal taxRate,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal returnedQty,
        String lotNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        Long locationId,
        String serialNos,
        String remark
) {
}

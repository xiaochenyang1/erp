package com.tuowei.erp.sales.price.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesPriceResponse(
        Long id,
        Long customerId,
        String customerName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal listPrice,
        BigDecimal minPrice,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String remark
) {
}

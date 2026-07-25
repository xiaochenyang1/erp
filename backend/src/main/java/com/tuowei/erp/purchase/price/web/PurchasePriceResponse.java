package com.tuowei.erp.purchase.price.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchasePriceResponse(
        Long id,
        Long supplierId,
        String supplierName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal listPrice,
        BigDecimal maxPrice,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String remark
) {
}

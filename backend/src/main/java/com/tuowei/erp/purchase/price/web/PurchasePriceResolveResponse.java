package com.tuowei.erp.purchase.price.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchasePriceResolveResponse(
        Long productId,
        Long supplierId,
        LocalDate bizDate,
        boolean matched,
        String matchLevel,
        Long priceId,
        BigDecimal listPrice,
        BigDecimal maxPrice
) {
}

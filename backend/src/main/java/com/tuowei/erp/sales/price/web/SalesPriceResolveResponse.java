package com.tuowei.erp.sales.price.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesPriceResolveResponse(
        Long productId,
        Long customerId,
        LocalDate bizDate,
        boolean matched,
        String matchLevel,
        Long priceId,
        BigDecimal listPrice,
        BigDecimal minPrice
) {
}

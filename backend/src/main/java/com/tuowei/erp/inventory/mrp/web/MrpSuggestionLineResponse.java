package com.tuowei.erp.inventory.mrp.web;

import java.math.BigDecimal;

public record MrpSuggestionLineResponse(
        Long productId,
        String productCode,
        String productName,
        String suggestionType,
        BigDecimal demandQty,
        BigDecimal onHandQty,
        BigDecimal openSupplyQty,
        BigDecimal netQty,
        Long bomId,
        String reason
) {
}

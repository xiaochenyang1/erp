package com.tuowei.erp.inventory.mrp.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MrpSuggestionLineResponse(
        Long id,
        Long runId,
        Integer lineNo,
        Long productId,
        String productCode,
        String productName,
        String suggestionType,
        BigDecimal demandQty,
        BigDecimal onHandQty,
        BigDecimal openSupplyQty,
        BigDecimal netQty,
        Long bomId,
        String reason,
        String status,
        String convertedBizType,
        Long convertedBizId,
        String convertedBizNo,
        LocalDateTime convertedTime
) {
}

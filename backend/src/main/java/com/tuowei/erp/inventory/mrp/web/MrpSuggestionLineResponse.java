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
        LocalDateTime convertedTime,
        Long warehouseId, Long materialWarehouseId, Integer bomVersion, Integer bomLevel, com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot.Node trace
) {
    public MrpSuggestionLineResponse(
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
        this(id, runId, lineNo, productId, productCode, productName, suggestionType, demandQty, onHandQty, openSupplyQty, netQty, bomId, reason, status, convertedBizType, convertedBizId, convertedBizNo, convertedTime, null, null, null, null, null);
    }

}

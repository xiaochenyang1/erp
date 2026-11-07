package com.tuowei.erp.inventory.mrp.web;

import java.time.LocalDateTime;
import java.util.List;

public record MrpRunResponse(
        Long id,
        String runNo,
        String asOfDate,
        String status,
        int purchaseCount,
        int productionCount,
        LocalDateTime createdTime,
        List<MrpSuggestionLineResponse> purchaseLines,
        List<MrpSuggestionLineResponse> productionLines,
        int algorithmVersion, com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot calculationSnapshot
) {
    public MrpRunResponse(
        Long id,
        String runNo,
        String asOfDate,
        String status,
        int purchaseCount,
        int productionCount,
        LocalDateTime createdTime,
        List<MrpSuggestionLineResponse> purchaseLines,
        List<MrpSuggestionLineResponse> productionLines
    ) {
        this(id, runNo, asOfDate, status, purchaseCount, productionCount, createdTime, purchaseLines, productionLines, 1, null);
    }

}

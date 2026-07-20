package com.tuowei.erp.inventory.mrp.web;

import java.util.List;

public record MrpRunResponse(
        String asOfDate,
        int purchaseCount,
        int productionCount,
        List<MrpSuggestionLineResponse> purchaseLines,
        List<MrpSuggestionLineResponse> productionLines
) {
}

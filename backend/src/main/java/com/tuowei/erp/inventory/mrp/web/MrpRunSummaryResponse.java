package com.tuowei.erp.inventory.mrp.web;

import java.time.LocalDateTime;

public record MrpRunSummaryResponse(
        Long id,
        String runNo,
        String asOfDate,
        String status,
        int purchaseCount,
        int productionCount,
        LocalDateTime createdTime
) {
}

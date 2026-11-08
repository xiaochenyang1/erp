package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProductionCostPeriodCloseBatchResponse(
        Long periodId,
        String periodMonth,
        LocalDate businessDate,
        int requestedCount,
        int successCount,
        int failureCount,
        BigDecimal closedVarianceAmount,
        Long operatorId,
        LocalDateTime executedTime,
        List<ProductionCostPeriodCloseBatchItemResponse> results
) {
}

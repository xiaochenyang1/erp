package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductionCostPeriodClosePreviewResponse(
        Long periodId,
        String periodMonth,
        LocalDate startDate,
        LocalDate endDate,
        String periodStatus,
        int candidateCount,
        BigDecimal positiveVarianceAmount,
        BigDecimal negativeVarianceAmount,
        BigDecimal netVarianceAmount,
        List<ProductionCostPeriodCloseItemResponse> items
) {
}

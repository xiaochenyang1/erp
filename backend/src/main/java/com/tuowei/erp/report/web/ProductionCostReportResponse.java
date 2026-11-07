package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCostReportResponse(
        Long orderId,
        String orderNo,
        Long productId,
        String productCode,
        String productName,
        String status,
        LocalDate plannedStartDate,
        LocalDate plannedFinishDate,
        BigDecimal plannedQty,
        BigDecimal completedQty,
        BigDecimal completionRate,
        BigDecimal materialCost,
        BigDecimal finishedGoodsCost,
        BigDecimal workInProgressCost,
        BigDecimal completionUnitCost,
        String costStatus
) {
}

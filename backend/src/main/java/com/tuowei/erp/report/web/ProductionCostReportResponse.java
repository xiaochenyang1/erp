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
        BigDecimal laborCost,
        BigDecimal overheadCost,
        BigDecimal totalInputCost,
        BigDecimal finishedMaterialCost,
        BigDecimal finishedLaborCost,
        BigDecimal finishedOverheadCost,
        BigDecimal finishedGoodsCost,
        BigDecimal materialWorkInProgressCost,
        BigDecimal laborWorkInProgressCost,
        BigDecimal overheadWorkInProgressCost,
        BigDecimal workInProgressCost,
        BigDecimal productionCostLedgerAmount,
        BigDecimal reconciliationDifference,
        BigDecimal completionUnitCost,
        String costStatus
) {
}

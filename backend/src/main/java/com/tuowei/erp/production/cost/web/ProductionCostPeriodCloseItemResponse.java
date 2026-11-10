package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionCostPeriodCloseItemResponse(
        Long orderId,
        String orderNo,
        Long productId,
        LocalDate completionDate,
        LocalDate latestActivityDate,
        BigDecimal issuedAmount,
        BigDecimal laborAmount,
        BigDecimal overheadAmount,
        BigDecimal finishedAmount,
        BigDecimal unsettledVarianceAmount
) {
}

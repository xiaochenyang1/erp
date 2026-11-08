package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;

public record ProductionCostPeriodCloseBatchItemResponse(
        Long orderId,
        String orderNo,
        boolean success,
        Long closeId,
        String closeNo,
        BigDecimal varianceAmount,
        String errorCode,
        String errorMessage
) {
}

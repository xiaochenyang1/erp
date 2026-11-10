package com.tuowei.erp.production.cost.web;

import java.time.LocalDateTime;

public record ProductionCostPeriodCloseEvidenceResponse(
        Long id,
        Long operatorId,
        String operatorName,
        LocalDateTime auditTime,
        String message,
        ProductionCostPeriodCloseBatchResponse batch
) {
}

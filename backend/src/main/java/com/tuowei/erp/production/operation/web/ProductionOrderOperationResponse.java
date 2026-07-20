package com.tuowei.erp.production.operation.web;

import java.math.BigDecimal;

public record ProductionOrderOperationResponse(
        Long id,
        Long orderId,
        Integer lineNo,
        String operationCode,
        String operationName,
        Long workCenterId,
        String workCenterName,
        BigDecimal plannedQty,
        BigDecimal reportedQty,
        BigDecimal qualifiedQty,
        BigDecimal scrapQty,
        String status,
        String remark
) {
}

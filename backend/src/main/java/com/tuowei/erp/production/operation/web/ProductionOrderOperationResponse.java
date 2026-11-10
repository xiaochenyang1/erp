package com.tuowei.erp.production.operation.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductionOrderOperationResponse(
        Long id,
        Long orderId,
        Integer lineNo,
        String operationCode,
        String operationName,
        Long workCenterId,
        String workCenterName,
        BigDecimal standardMinutes,
        BigDecimal plannedQty,
        BigDecimal reportedQty,
        BigDecimal qualifiedQty,
        BigDecimal scrapQty,
        String status,
        BigDecimal scheduledMinutes,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        String remark
) {
}

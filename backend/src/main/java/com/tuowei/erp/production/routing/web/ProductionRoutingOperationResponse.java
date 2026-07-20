package com.tuowei.erp.production.routing.web;

import java.math.BigDecimal;

public record ProductionRoutingOperationResponse(
        Long id,
        Integer lineNo,
        String operationCode,
        String operationName,
        Long workCenterId,
        String workCenterCode,
        String workCenterName,
        BigDecimal standardMinutes,
        String remark
) {
}

package com.tuowei.erp.production.schedule.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductionScheduleItemResponse(
        Long operationId,
        Long orderId,
        String orderNo,
        Long productId,
        LocalDate dueDate,
        Integer lineNo,
        String operationCode,
        String operationName,
        Long workCenterId,
        String workCenterCode,
        String workCenterName,
        BigDecimal remainingQty,
        BigDecimal standardMinutes,
        BigDecimal requiredMinutes,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        Long capacityWaitMinutes,
        boolean delayed,
        Long lateMinutes,
        String scheduleStatus,
        String reason
) {
}

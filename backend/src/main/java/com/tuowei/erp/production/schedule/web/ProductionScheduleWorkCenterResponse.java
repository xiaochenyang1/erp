package com.tuowei.erp.production.schedule.web;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ProductionScheduleWorkCenterResponse(
        Long workCenterId,
        String workCenterCode,
        String workCenterName,
        Integer dailyCapacityMinutes,
        LocalTime workdayStartTime,
        BigDecimal horizonCapacityMinutes,
        BigDecimal scheduledMinutes,
        BigDecimal utilizationPercent
) {
}

package com.tuowei.erp.production.workcenter.web;

import java.time.LocalTime;

public record ProductionWorkCenterResponse(
        Long id,
        String workCenterCode,
        String workCenterName,
        Integer dailyCapacityMinutes,
        LocalTime workdayStartTime,
        String status,
        String remark
) {
}

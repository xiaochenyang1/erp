package com.tuowei.erp.production.schedule.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProductionScheduleResponse(
        LocalDate startDate,
        LocalDate endDate,
        boolean committed,
        int orderCount,
        int operationCount,
        int scheduledCount,
        int unscheduledCount,
        int delayedCount,
        LocalDateTime generatedTime,
        List<ProductionScheduleItemResponse> items,
        List<ProductionScheduleWorkCenterResponse> workCenters
) {
}

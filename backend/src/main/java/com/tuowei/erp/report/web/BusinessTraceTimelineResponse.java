package com.tuowei.erp.report.web;

import java.time.LocalDateTime;

public record BusinessTraceTimelineResponse(
        String id,
        String eventType,
        String title,
        String bizNo,
        String description,
        LocalDateTime occurredAt,
        String status,
        String severity,
        String route
) {
}

package com.tuowei.erp.report.web;

import java.time.LocalDateTime;

public record BusinessTraceExceptionTicketResponse(
        Long id,
        String ticketNo,
        String category,
        String priority,
        String title,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String status,
        Long assigneeUserId,
        LocalDateTime dueTime,
        LocalDateTime updatedTime,
        String route
) {
}

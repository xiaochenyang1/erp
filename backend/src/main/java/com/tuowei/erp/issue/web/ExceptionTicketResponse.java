package com.tuowei.erp.issue.web;

import java.time.LocalDateTime;
import java.util.List;

public record ExceptionTicketResponse(
        Long id,
        String ticketNo,
        String category,
        String priority,
        String title,
        String description,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String sourceRoute,
        Boolean traceable,
        String traceKeyword,
        String traceRoute,
        String status,
        Long assigneeUserId,
        LocalDateTime dueTime,
        Long resolvedBy,
        LocalDateTime resolvedTime,
        String resolution,
        Long createdBy,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        List<ExceptionTicketEventResponse> events
) {
}

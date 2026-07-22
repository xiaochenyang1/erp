package com.tuowei.erp.workflow.web;

import java.time.LocalDateTime;

public record WorkflowTaskResponse(
        Long id,
        Long instanceId,
        String businessType,
        Long businessId,
        String businessNo,
        String title,
        Long approverUserId,
        String status,
        LocalDateTime dueTime,
        boolean overdue,
        LocalDateTime escalatedTime,
        Integer escalationCount,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}

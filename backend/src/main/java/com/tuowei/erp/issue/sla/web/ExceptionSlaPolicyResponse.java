package com.tuowei.erp.issue.sla.web;

import java.time.LocalDateTime;

public record ExceptionSlaPolicyResponse(
        Long id,
        String category,
        String priority,
        Integer dueHours,
        boolean escalationEnabled,
        String escalateToPriority,
        boolean enabled,
        String remark,
        LocalDateTime updatedTime
) {
}

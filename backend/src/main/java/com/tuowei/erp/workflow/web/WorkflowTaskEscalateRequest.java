package com.tuowei.erp.workflow.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowTaskEscalateRequest(
        @NotNull(message = "{workflow.task.target.required}") Long targetUserId,
        @Size(max = 255, message = "comment长度不能超过255") String comment
) {
}

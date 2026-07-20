package com.tuowei.erp.workflow.web;

import jakarta.validation.constraints.NotNull;

public record WorkflowTaskTransferRequest(
        @NotNull(message = "targetUserId不能为空") Long targetUserId,
        String comment
) {
}

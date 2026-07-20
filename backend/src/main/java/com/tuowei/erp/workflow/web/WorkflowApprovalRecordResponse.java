package com.tuowei.erp.workflow.web;

import java.time.LocalDateTime;

public record WorkflowApprovalRecordResponse(
        Long id,
        String action,
        Long operatorUserId,
        String comment,
        LocalDateTime actionTime
) {
}

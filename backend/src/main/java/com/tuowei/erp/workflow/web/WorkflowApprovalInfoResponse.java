package com.tuowei.erp.workflow.web;

import java.time.LocalDateTime;
import java.util.List;

public record WorkflowApprovalInfoResponse(
        Long instanceId,
        String status,
        Long submitUserId,
        LocalDateTime submitTime,
        LocalDateTime completedTime,
        List<WorkflowApprovalRecordResponse> records
) {
}

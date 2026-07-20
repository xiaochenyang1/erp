package com.tuowei.erp.workflow.web;

import java.time.LocalDateTime;

public record WorkflowRecordResponse(
        Long id,
        Long instanceId,
        String businessType,
        Long businessId,
        String businessNo,
        String action,
        Long operatorUserId,
        String comment,
        LocalDateTime actionTime
) {
}

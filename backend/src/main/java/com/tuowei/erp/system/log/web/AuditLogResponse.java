package com.tuowei.erp.system.log.web;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String auditType,
        String businessType,
        Long businessId,
        String businessNo,
        String action,
        Long operatorId,
        String operatorName,
        String snapshotJson,
        String message,
        LocalDateTime auditTime
) {
}

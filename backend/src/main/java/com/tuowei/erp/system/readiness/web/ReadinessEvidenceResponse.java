package com.tuowei.erp.system.readiness.web;

import java.time.LocalDateTime;

public record ReadinessEvidenceResponse(
        Long id,
        Long runId,
        Long itemId,
        String evidenceType,
        String requestMethod,
        String requestUri,
        Integer httpStatus,
        String businessType,
        Long businessId,
        String businessNo,
        String summary,
        String detail,
        String attachmentBusinessType,
        Long attachmentBusinessId,
        Long recordedBy,
        LocalDateTime recordedTime
) {
}

package com.tuowei.erp.system.readiness.web;

public record ReadinessEvidenceCreateRequest(
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
        Long attachmentBusinessId
) {
}

package com.tuowei.erp.system.readiness.web;

import java.time.LocalDateTime;
import java.util.List;

public record ReadinessItemResponse(
        Long id,
        Long runId,
        String itemCode,
        String itemName,
        String category,
        String priority,
        String status,
        String expectedResult,
        String actualResult,
        String failureReason,
        Long executedBy,
        LocalDateTime executedTime,
        LocalDateTime createdTime,
        List<ReadinessEvidenceResponse> evidence
) {
}

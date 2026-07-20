package com.tuowei.erp.system.readiness.web;

import java.time.LocalDateTime;
import java.util.List;

public record ReadinessPreflightResponse(
        String overallStatus,
        LocalDateTime checkedAt,
        List<ReadinessPreflightItemResponse> items
) {
}

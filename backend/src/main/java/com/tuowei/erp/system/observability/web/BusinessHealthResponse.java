package com.tuowei.erp.system.observability.web;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessHealthResponse(
        String overallStatus,
        LocalDateTime generatedAt,
        List<BusinessHealthCheckResponse> checks
) {
}

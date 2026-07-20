package com.tuowei.erp.system.readiness.web;

import java.util.List;

public record ReadinessPreflightItemResponse(
        String code,
        String status,
        String severity,
        String summary,
        long count,
        List<String> sample
) {
}

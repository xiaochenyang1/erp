package com.tuowei.erp.system.observability.web;

public record BusinessHealthCheckResponse(
        String code,
        String name,
        String status,
        long count,
        long threshold,
        String summary
) {
}

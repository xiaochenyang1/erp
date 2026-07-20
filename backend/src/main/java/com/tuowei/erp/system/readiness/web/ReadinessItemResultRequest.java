package com.tuowei.erp.system.readiness.web;

public record ReadinessItemResultRequest(
        String status,
        String actualResult,
        String failureReason
) {
}

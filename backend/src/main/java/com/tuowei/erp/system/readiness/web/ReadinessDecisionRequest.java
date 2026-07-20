package com.tuowei.erp.system.readiness.web;

public record ReadinessDecisionRequest(
        String decision,
        String status,
        String decisionComment
) {
}

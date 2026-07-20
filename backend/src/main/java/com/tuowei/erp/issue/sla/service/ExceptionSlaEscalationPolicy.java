package com.tuowei.erp.issue.sla.service;

public record ExceptionSlaEscalationPolicy(
        boolean enabled,
        String targetPriority
) {
}

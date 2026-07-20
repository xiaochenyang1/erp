package com.tuowei.erp.issue.rule.service;

public record ExceptionRuleFinding(
        String sourceType,
        Long sourceId,
        String sourceNo,
        String sourceRoute,
        String hitKey,
        String title,
        String description,
        String triggerValue,
        String thresholdValue
) {
}

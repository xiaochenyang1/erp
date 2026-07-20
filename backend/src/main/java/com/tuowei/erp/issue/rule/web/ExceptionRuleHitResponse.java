package com.tuowei.erp.issue.rule.web;

import java.time.LocalDateTime;

public record ExceptionRuleHitResponse(
        Long id,
        Long ruleId,
        String ruleCode,
        String ruleType,
        String sourceType,
        Long sourceId,
        String sourceNo,
        String sourceRoute,
        String hitKey,
        String title,
        String description,
        String triggerValue,
        String thresholdValue,
        Long ticketId,
        Integer hitCount,
        LocalDateTime firstHitTime,
        LocalDateTime lastHitTime
) {
}

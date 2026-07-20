package com.tuowei.erp.issue.rule.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExceptionRuleResponse(
        Long id,
        String ruleCode,
        String ruleName,
        String ruleType,
        String category,
        String priority,
        BigDecimal thresholdValue,
        String thresholdUnit,
        boolean enabled,
        Long assigneeUserId,
        Integer scheduleIntervalMinutes,
        LocalDateTime nextScanTime,
        String remark,
        LocalDateTime lastScanTime,
        String lastScanStatus,
        Integer lastHitCount,
        Integer lastTicketCreatedCount,
        String lastErrorMessage,
        LocalDateTime updatedTime
) {
}

package com.tuowei.erp.issue.rule.web;

import java.time.LocalDateTime;

public record ExceptionRuleScanResultResponse(
        Long ruleId,
        String ruleCode,
        String ruleType,
        String status,
        int hitCount,
        int ticketCreatedCount,
        int duplicateTicketCount,
        String message,
        LocalDateTime scannedAt
) {
}

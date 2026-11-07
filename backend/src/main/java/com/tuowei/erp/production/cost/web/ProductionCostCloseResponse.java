package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductionCostCloseResponse(
        Long id,
        String closeNo,
        Long orderId,
        LocalDate businessDate,
        BigDecimal amount,
        String status,
        String entryKind,
        Long reversalOfCloseId,
        String reversalOfCloseNo,
        Long reversalEntryId,
        String reversalCloseNo,
        String reason,
        String remark,
        LocalDateTime createdTime
) {
}

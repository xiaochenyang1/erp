package com.tuowei.erp.production.cost.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductionCostEntryResponse(
        Long id,
        String costNo,
        Long orderId,
        Long operationId,
        String costType,
        LocalDate businessDate,
        BigDecimal amount,
        String status,
        String entryKind,
        Long reversalOfEntryId,
        String reversalOfCostNo,
        Long correctionOfEntryId,
        String correctionOfCostNo,
        Long reversalEntryId,
        String reversalCostNo,
        Long correctionEntryId,
        String correctionCostNo,
        String remark,
        LocalDateTime createdTime
) {
}

package com.tuowei.erp.report.web;

import java.math.BigDecimal;

public record BusinessTraceSummaryResponse(
        int documentCount,
        int timelineCount,
        BigDecimal openReceivableAmount,
        BigDecimal openPayableAmount,
        BigDecimal inventoryMovementQuantity,
        int failedOperationCount,
        int openExceptionTicketCount
) {
}

package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTransactionReportResponse(
        Long id,
        Long warehouseId,
        Long productId,
        String bizType,
        String bizNo,
        Long bizLineId,
        String direction,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal unitCost,
        LocalDateTime occurredTime,
        String remark
) {
}

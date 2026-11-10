package com.tuowei.erp.inventory.adjust.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryAdjustmentResponse(
        Long id,
        String adjustmentNo,
        Long warehouseId,
        LocalDate adjustmentDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        String remark,
        List<InventoryAdjustmentLineResponse> lines,
        String sourceType,
        Long sourceId,
        String sourceNo,
        Long createdBy,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}

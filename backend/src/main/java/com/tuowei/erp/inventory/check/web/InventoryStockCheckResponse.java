package com.tuowei.erp.inventory.check.web;

import java.time.LocalDate;
import java.util.List;

public record InventoryStockCheckResponse(
        Long id,
        String checkNo,
        Long warehouseId,
        LocalDate checkDate,
        String status,
        Long generatedAdjustmentId,
        String generatedAdjustmentNo,
        String remark,
        List<InventoryStockCheckLineResponse> lines
) {
}

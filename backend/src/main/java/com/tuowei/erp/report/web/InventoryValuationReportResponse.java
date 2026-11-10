package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryValuationReportResponse(
        String rowKey,
        LocalDate periodStart,
        LocalDate asOfDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal openingQty,
        BigDecimal openingAmount,
        BigDecimal inboundQty,
        BigDecimal inboundAmount,
        BigDecimal outboundQty,
        BigDecimal outboundAmount,
        BigDecimal closingQty,
        BigDecimal closingAmount,
        BigDecimal averageUnitCost
) {
}

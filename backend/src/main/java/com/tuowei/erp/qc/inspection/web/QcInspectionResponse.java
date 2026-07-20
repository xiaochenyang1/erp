package com.tuowei.erp.qc.inspection.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QcInspectionResponse(
        Long id,
        String inspectionNo,
        String inspectionType,
        Long receiptId,
        Long deliveryId,
        Long productionOrderId,
        Long orderId,
        Long warehouseId,
        Long supplierId,
        LocalDate inspectionDate,
        String status,
        BigDecimal totalQty,
        BigDecimal qualifiedQty,
        BigDecimal unqualifiedQty,
        String remark,
        List<QcInspectionLineResponse> lines
) {
}

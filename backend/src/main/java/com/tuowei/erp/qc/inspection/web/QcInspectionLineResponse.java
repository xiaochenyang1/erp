package com.tuowei.erp.qc.inspection.web;

import java.math.BigDecimal;

public record QcInspectionLineResponse(
        Long id,
        Integer lineNo,
        Long receiptLineId,
        Long deliveryLineId,
        Long productId,
        BigDecimal inspectedQty,
        BigDecimal qualifiedQty,
        BigDecimal unqualifiedQty,
        String defectReason,
        String remark
) {
}

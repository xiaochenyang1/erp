package com.tuowei.erp.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BusinessTraceDocumentResponse(
        String id,
        String documentType,
        String documentLabel,
        Long documentId,
        String bizNo,
        String title,
        String status,
        String secondaryStatus,
        LocalDate bizDate,
        String partnerType,
        Long partnerId,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        String route
) {
}

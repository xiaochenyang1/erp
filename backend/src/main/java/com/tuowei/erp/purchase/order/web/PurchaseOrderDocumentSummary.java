package com.tuowei.erp.purchase.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseOrderDocumentSummary(
        Long id,
        String documentNo,
        String documentType,
        LocalDate documentDate,
        String status,
        BigDecimal amount
) {
}

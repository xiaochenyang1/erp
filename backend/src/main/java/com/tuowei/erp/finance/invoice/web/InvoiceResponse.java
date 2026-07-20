package com.tuowei.erp.finance.invoice.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id,
        String invoiceNo,
        String invoiceType,
        String partnerName,
        BigDecimal amount,
        BigDecimal taxAmount,
        LocalDate invoiceDate,
        String relatedBizType,
        Long relatedBizId,
        String status,
        String remark
) {
}

package com.tuowei.erp.sales.quote.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesQuoteResponse(
        Long id,
        String quoteNo,
        Long customerId,
        String customerName,
        LocalDate quoteDate,
        LocalDate validUntil,
        String status,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        Long convertedOrderId,
        String remark,
        List<SalesQuoteLineResponse> lines
) {
}

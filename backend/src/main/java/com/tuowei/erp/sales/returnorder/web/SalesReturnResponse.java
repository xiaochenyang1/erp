package com.tuowei.erp.sales.returnorder.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReturnResponse(
        Long id,
        String returnNo,
        Long deliveryId,
        Long warehouseId,
        LocalDate returnDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        List<SalesReturnLineResponse> lines
) {
}

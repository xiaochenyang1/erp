package com.tuowei.erp.sales.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String orderNo,
        Long customerId,
        Long warehouseId,
        String customerName,
        LocalDate orderDate,
        LocalDate deliveryDate,
        String status,
        String approvalStatus,
        String deliveryStatus,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        List<SalesOrderLineResponse> lines
) {
}

package com.tuowei.erp.sales.order.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String orderNo,
        Long contractId,
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
    public SalesOrderResponse(Long id, String orderNo, Long customerId, Long warehouseId, String customerName,
                              LocalDate orderDate, LocalDate deliveryDate, String status, String approvalStatus,
                              String deliveryStatus, BigDecimal totalQuantity, BigDecimal totalAmount,
                              BigDecimal totalTaxAmount, String remark, List<SalesOrderLineResponse> lines) {
        this(id, orderNo, null, customerId, warehouseId, customerName, orderDate, deliveryDate, status,
                approvalStatus, deliveryStatus, totalQuantity, totalAmount, totalTaxAmount, remark, lines);
    }
}

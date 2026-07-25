package com.tuowei.erp.sales.delivery.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesDeliveryResponse(
        Long id,
        String deliveryNo,
        Long orderId,
        Long warehouseId,
        LocalDate deliveryDate,
        String status,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        BigDecimal totalTaxAmount,
        String remark,
        String carrierName,
        String trackingNo,
        String logisticsStatus,
        List<SalesDeliveryLineResponse> lines
) {
}

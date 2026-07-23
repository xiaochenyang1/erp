package com.tuowei.erp.sales.order.web;

import java.math.BigDecimal;

public record SalesOrderCreditPreviewResponse(
        Long customerId,
        BigDecimal creditLimit,
        BigDecimal outstandingReceivable,
        BigDecimal openOrderExposure,
        BigDecimal currentExposure,
        BigDecimal orderAmount,
        BigDecimal projectedExposure,
        BigDecimal availableCredit,
        BigDecimal projectedAvailableCredit,
        boolean unlimited,
        boolean exceeded
) {
}

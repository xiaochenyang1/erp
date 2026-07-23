package com.tuowei.erp.sales.order.service;

import java.math.BigDecimal;

public record SalesCreditPreview(
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

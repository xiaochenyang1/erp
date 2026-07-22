package com.tuowei.erp.sales.order.service;

import java.math.BigDecimal;

public record SalesCreditExposure(
        BigDecimal outstandingReceivable,
        BigDecimal openOrderExposure,
        BigDecimal totalExposure
) {
}

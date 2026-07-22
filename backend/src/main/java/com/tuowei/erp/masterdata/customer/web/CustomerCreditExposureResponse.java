package com.tuowei.erp.masterdata.customer.web;

import java.math.BigDecimal;

public record CustomerCreditExposureResponse(
        Long customerId,
        BigDecimal creditLimit,
        BigDecimal outstandingReceivable,
        BigDecimal openOrderExposure,
        BigDecimal totalExposure,
        BigDecimal availableCredit,
        boolean unlimited,
        boolean exceeded
) {
}

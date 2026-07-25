package com.tuowei.erp.masterdata.customer.web;

import java.math.BigDecimal;

public record CustomerResponse(
        Long id,
        String customerCode,
        String customerName,
        String customerType,
        String contactName,
        String contactPhone,
        String email,
        String settlementMethod,
        BigDecimal creditLimit,
        Integer creditPeriod,
        String address,
        String status,
        String remark
) {
}

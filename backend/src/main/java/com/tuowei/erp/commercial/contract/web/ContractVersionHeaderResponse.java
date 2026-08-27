package com.tuowei.erp.commercial.contract.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractVersionHeaderResponse(
        String contractNo,
        String contractType,
        Long customerId,
        Long supplierId,
        String contractName,
        LocalDate signedDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal totalAmount,
        String remark
) {}

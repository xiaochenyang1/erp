package com.tuowei.erp.commercial.contract.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractResponse(
        Long id,
        String contractNo,
        String contractType,
        Long customerId,
        String customerName,
        Long supplierId,
        String supplierName,
        String contractName,
        LocalDate signedDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        BigDecimal totalAmount,
        String remark,
        List<ContractLineResponse> lines
) {
}

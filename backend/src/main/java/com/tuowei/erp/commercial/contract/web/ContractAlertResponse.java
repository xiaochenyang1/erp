package com.tuowei.erp.commercial.contract.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractAlertResponse(
        Long contractId,
        String contractNo,
        String contractName,
        String contractType,
        LocalDate effectiveTo,
        Long daysToExpiry,
        BigDecimal executionRate,
        List<String> alertTypes
) {
}

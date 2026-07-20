package com.tuowei.erp.finance.statement.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PartnerStatementResponse(
        String partnerType,
        Long partnerId,
        String partnerName,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal openingBalance,
        BigDecimal totalIncrease,
        BigDecimal totalDecrease,
        BigDecimal closingBalance,
        List<PartnerStatementLineResponse> lines
) {
}

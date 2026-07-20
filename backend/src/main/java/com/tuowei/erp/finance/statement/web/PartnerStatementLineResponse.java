package com.tuowei.erp.finance.statement.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PartnerStatementLineResponse(
        LocalDate bizDate,
        String docType,
        String docNo,
        String direction,
        BigDecimal amount,
        BigDecimal balance,
        String remark
) {
}

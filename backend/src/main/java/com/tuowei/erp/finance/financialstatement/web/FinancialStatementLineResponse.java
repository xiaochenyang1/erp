package com.tuowei.erp.finance.financialstatement.web;

import java.math.BigDecimal;

public record FinancialStatementLineResponse(
        String itemCode,
        String itemName,
        String lineType,
        String subjectCode,
        BigDecimal amount
) {
}

package com.tuowei.erp.finance.fund.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankStatementCreateRequest(
        Long fundAccountId,
        String externalTxnNo,
        LocalDate transactionDate,
        String direction,
        BigDecimal amount,
        String counterpartyName,
        String summary,
        String remark
) {
}

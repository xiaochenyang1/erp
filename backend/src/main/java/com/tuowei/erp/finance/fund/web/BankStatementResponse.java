package com.tuowei.erp.finance.fund.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BankStatementResponse(
        Long id,
        Long fundAccountId,
        String statementNo,
        String externalTxnNo,
        LocalDate transactionDate,
        String direction,
        BigDecimal amount,
        String counterpartyName,
        String summary,
        String status,
        String matchedBizType,
        Long matchedBizId,
        String matchedBizNo,
        LocalDateTime matchedTime,
        Long matchedBy,
        String unmatchReason,
        String remark,
        LocalDateTime createdTime
) {
}

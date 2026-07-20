package com.tuowei.erp.finance.fund.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAccountResponse(
        Long id,
        String accountCode,
        String accountName,
        String accountType,
        String bankName,
        String bankAccountNo,
        String currencyCode,
        BigDecimal openingBalance,
        String status,
        String remark,
        LocalDateTime createdTime
) {
}

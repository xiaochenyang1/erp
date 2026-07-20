package com.tuowei.erp.finance.fund.web;

import java.math.BigDecimal;

public record FundAccountCreateRequest(
        String accountCode,
        String accountName,
        String accountType,
        String bankName,
        String bankAccountNo,
        String currencyCode,
        BigDecimal openingBalance,
        String remark
) {
}

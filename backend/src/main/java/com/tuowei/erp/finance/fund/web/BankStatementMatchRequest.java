package com.tuowei.erp.finance.fund.web;

public record BankStatementMatchRequest(
        String bizType,
        Long bizId,
        String remark
) {
}

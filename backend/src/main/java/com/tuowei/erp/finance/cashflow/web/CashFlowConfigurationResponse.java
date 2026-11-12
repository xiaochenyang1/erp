package com.tuowei.erp.finance.cashflow.web;

import java.util.List;

public record CashFlowConfigurationResponse(
        String formulaVersion,
        List<String> cashSubjectCodes,
        List<CashFlowConfigurationItemResponse> items,
        List<String> validationErrors
) {
}

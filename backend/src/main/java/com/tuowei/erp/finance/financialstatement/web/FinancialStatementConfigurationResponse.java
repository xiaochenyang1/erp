package com.tuowei.erp.finance.financialstatement.web;

import java.util.List;

public record FinancialStatementConfigurationResponse(
        String formulaVersion,
        String statementType,
        List<FinancialStatementConfigurationItemResponse> items,
        List<String> validationErrors
) {
}

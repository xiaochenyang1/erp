package com.tuowei.erp.finance.financialstatement.model;

import java.util.List;
import java.util.Map;

public record FinancialStatementDefinition(
        String formulaVersion,
        String statementType,
        List<FinancialStatementItemDefinition> items,
        Map<String, String> subjectItemCodes,
        List<String> validationErrors
) {
}

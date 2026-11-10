package com.tuowei.erp.finance.cashflow.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CashFlowDefinition(
        String formulaVersion,
        List<CashFlowItemDefinition> items,
        Set<String> cashSubjectCodes,
        Map<String, String> subjectItemCodes,
        List<String> validationErrors
) {
}

package com.tuowei.erp.finance.financialstatement.model;

import java.util.List;

public record FinancialStatementItemDefinition(
        String itemCode,
        String itemName,
        String sectionCode,
        String amountDirection,
        String formulaCode,
        int sortNo,
        List<String> subjectCodes
) {
}

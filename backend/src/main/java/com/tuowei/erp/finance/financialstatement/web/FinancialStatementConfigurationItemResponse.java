package com.tuowei.erp.finance.financialstatement.web;

import java.util.List;

public record FinancialStatementConfigurationItemResponse(
        String itemCode,
        String itemName,
        String sectionCode,
        String amountDirection,
        String formulaCode,
        int sortNo,
        List<String> subjectCodes
) {
}

package com.tuowei.erp.finance.cashflow.model;

import java.util.List;

public record CashFlowItemDefinition(
        String itemCode,
        String itemName,
        String activityType,
        int sortNo,
        List<String> subjectCodes
) {
}

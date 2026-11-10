package com.tuowei.erp.finance.cashflow.web;

import java.util.List;

public record CashFlowConfigurationItemResponse(
        String itemCode,
        String itemName,
        String activityType,
        int sortNo,
        List<String> subjectCodes
) {
}

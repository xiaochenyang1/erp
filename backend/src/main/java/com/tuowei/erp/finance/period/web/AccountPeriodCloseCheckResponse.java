package com.tuowei.erp.finance.period.web;

import java.util.List;

public record AccountPeriodCloseCheckResponse(
        Long periodId,
        String periodMonth,
        boolean passed,
        List<AccountPeriodCloseIssueResponse> issues,
        List<AccountPeriodCloseCheckItemResponse> checks
) {
}

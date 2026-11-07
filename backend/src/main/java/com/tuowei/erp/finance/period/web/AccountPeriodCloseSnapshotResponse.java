package com.tuowei.erp.finance.period.web;

import java.time.LocalDateTime;
import java.util.List;

public record AccountPeriodCloseSnapshotResponse(
        Long id,
        Long periodId,
        String actionType,
        boolean passed,
        int issueCount,
        Long checkedBy,
        LocalDateTime checkedTime,
        List<AccountPeriodCloseSnapshotItemResponse> items
) {
}

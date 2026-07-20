package com.tuowei.erp.finance.period.web;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountPeriodResponse(
        Long id,
        Integer periodYear,
        String periodMonth,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Long lockedBy,
        LocalDateTime lockedTime,
        Long closedBy,
        LocalDateTime closedTime,
        Long reopenedBy,
        LocalDateTime reopenedTime,
        String remark
) {
}

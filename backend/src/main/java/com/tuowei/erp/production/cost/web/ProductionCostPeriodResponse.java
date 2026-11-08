package com.tuowei.erp.production.cost.web;

import java.time.LocalDate;

public record ProductionCostPeriodResponse(
        Long id,
        Integer periodYear,
        String periodMonth,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {
}

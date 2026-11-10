package com.tuowei.erp.production.schedule.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ProductionScheduleRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}

package com.tuowei.erp.finance.period.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PeriodFinancialReportSnapshotResponse(
        Long id,
        String reportType,
        String formulaVersion,
        String configurationDigest,
        JsonNode configuration,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean verified,
        String payloadSha256,
        Long capturedBy,
        LocalDateTime capturedTime,
        JsonNode payload
) {
}

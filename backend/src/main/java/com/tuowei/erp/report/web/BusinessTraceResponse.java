package com.tuowei.erp.report.web;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessTraceResponse(
        String keyword,
        List<BusinessTraceDocumentResponse> documents,
        List<BusinessTraceTimelineResponse> timeline,
        List<BusinessTraceExceptionTicketResponse> exceptionTickets,
        BusinessTraceSummaryResponse summary,
        LocalDateTime generatedAt
) {
}

package com.tuowei.erp.dashboard.web;

import java.time.LocalDateTime;

public record OperationsDashboardFailedOperationResponse(
        Long id,
        String module,
        String operation,
        String bizNo,
        String message,
        String requestUri,
        LocalDateTime operationTime
) {
}

package com.tuowei.erp.dashboard.web;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        OperationsDashboardSummaryResponse summary,
        List<OperationsDashboardTodoResponse> todos,
        List<OperationsDashboardLowStockResponse> lowStock,
        List<OperationsDashboardFailedOperationResponse> failedOperations,
        LocalDateTime generatedAt
) {
}

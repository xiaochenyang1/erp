package com.tuowei.erp.dashboard.web;

import java.time.LocalDateTime;

public record OperationsDashboardTodoResponse(
        String id,
        String type,
        String title,
        String description,
        String priority,
        String route,
        LocalDateTime occurredAt
) {
}

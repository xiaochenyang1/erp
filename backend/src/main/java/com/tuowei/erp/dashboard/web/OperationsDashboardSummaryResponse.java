package com.tuowei.erp.dashboard.web;

import java.math.BigDecimal;

public record OperationsDashboardSummaryResponse(
        long pendingApprovals,
        long overdueApprovals,
        long lowStockAlerts,
        long openReceivables,
        BigDecimal openReceivableAmount,
        long openPayables,
        BigDecimal openPayableAmount,
        long todayPurchaseOrders,
        BigDecimal todaySalesAmount
) {
}

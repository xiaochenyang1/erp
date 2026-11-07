package com.tuowei.erp.dashboard.service;

import com.tuowei.erp.dashboard.web.OperationsDashboardTopSkuResponse;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;

import java.time.LocalDateTime;
import java.util.List;

/** Raw, tenant-scoped dashboard data loaded in one read snapshot. */
record OperationsDashboardSnapshot(
        LocalDateTime generatedAt,
        long pendingApprovalCount,
        long overdueApprovalCount,
        List<WorkflowTaskEntity> workflowTasks,
        List<InventoryLowStockResponse> lowStock,
        long openReceivableCount,
        List<ReceivableEntity> openReceivables,
        long openPayableCount,
        List<PayableEntity> openPayables,
        long todayPurchaseOrders,
        List<SalesOrderEntity> todaySales,
        List<OperationLogEntity> failedOperations,
        List<OperationsDashboardTopSkuResponse> topSkus
) {
}

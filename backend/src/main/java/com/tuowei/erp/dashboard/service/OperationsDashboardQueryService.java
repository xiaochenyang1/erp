package com.tuowei.erp.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.dashboard.web.OperationsDashboardTopSkuResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped read model loader for the operations dashboard. */
@Service
public class OperationsDashboardQueryService {

    private static final int TODO_LIMIT = 12;
    private static final int PREVIEW_LIMIT = 5;

    private final AuditMetadataFactory auditMetadataFactory;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final InventoryAlertService inventoryAlertService;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final OperationLogMapper operationLogMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final Clock clock;

    public OperationsDashboardQueryService(
            AuditMetadataFactory auditMetadataFactory,
            WorkflowTaskMapper workflowTaskMapper,
            InventoryAlertService inventoryAlertService,
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            OperationLogMapper operationLogMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            Clock clock
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.workflowTaskMapper = workflowTaskMapper;
        this.inventoryAlertService = inventoryAlertService;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.operationLogMapper = operationLogMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationsDashboardSnapshot load() {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDate today = LocalDate.now(clock);
        LocalDateTime generatedAt = LocalDateTime.now(clock);

        long pendingApprovalCount = workflowTaskMapper.selectCount(pendingWorkflowQuery(audit));
        long overdueApprovalCount = workflowTaskMapper.selectCount(overdueWorkflowQuery(audit, generatedAt));
        List<WorkflowTaskEntity> workflowTasks = workflowTaskMapper.selectList(pendingWorkflowQuery(audit)
                .orderByAsc(WorkflowTaskEntity::getCreatedTime)
                .last("limit " + TODO_LIMIT));

        List<InventoryLowStockResponse> lowStock = inventoryAlertService.listLowStock(null, null);

        LambdaQueryWrapper<ReceivableEntity> openReceivableQuery = openReceivableQuery(audit);
        long openReceivableCount = receivableMapper.selectCount(openReceivableQuery);
        List<ReceivableEntity> openReceivables = receivableMapper.selectList(openReceivableQuery(audit));

        LambdaQueryWrapper<PayableEntity> openPayableQuery = openPayableQuery(audit);
        long openPayableCount = payableMapper.selectCount(openPayableQuery);
        List<PayableEntity> openPayables = payableMapper.selectList(openPayableQuery(audit));

        long todayPurchaseOrders = purchaseOrderMapper.selectCount(todayPurchaseOrderQuery(audit, today));
        List<SalesOrderEntity> todaySales = salesOrderMapper.selectList(todaySalesOrderQuery(audit, today));

        List<OperationLogEntity> failedOperations = operationLogMapper.selectList(failedOperationQuery(audit)
                .orderByDesc(OperationLogEntity::getOperationTime)
                .last("limit " + PREVIEW_LIMIT));
        List<OperationsDashboardTopSkuResponse> topSkus = salesDeliveryLineMapper.selectTopSkus(
                audit.companyId(), audit.accountBookId(), today.minusDays(29), today, PREVIEW_LIMIT);

        return new OperationsDashboardSnapshot(
                generatedAt,
                pendingApprovalCount,
                overdueApprovalCount,
                workflowTasks,
                lowStock,
                openReceivableCount,
                openReceivables,
                openPayableCount,
                openPayables,
                todayPurchaseOrders,
                todaySales,
                failedOperations,
                topSkus
        );
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> pendingWorkflowQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getCompanyId, audit.companyId())
                .eq(WorkflowTaskEntity::getAccountBookId, audit.accountBookId())
                .eq(WorkflowTaskEntity::getApproverUserId, audit.userId())
                .eq(WorkflowTaskEntity::getStatus, "PENDING");
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> overdueWorkflowQuery(AuditMetadata audit, LocalDateTime now) {
        return pendingWorkflowQuery(audit)
                .isNotNull(WorkflowTaskEntity::getDueTime)
                .lt(WorkflowTaskEntity::getDueTime, now);
    }

    private LambdaQueryWrapper<ReceivableEntity> openReceivableQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, audit.companyId())
                .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceivableEntity::getDeletedFlag, 0)
                .notIn(ReceivableEntity::getStatus, "SETTLED", "CANCELLED", "CLOSED");
    }

    private LambdaQueryWrapper<PayableEntity> openPayableQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, audit.companyId())
                .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                .eq(PayableEntity::getDeletedFlag, 0)
                .notIn(PayableEntity::getStatus, "SETTLED", "CANCELLED", "CLOSED");
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> todayPurchaseOrderQuery(AuditMetadata audit, LocalDate today) {
        return new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getCompanyId, audit.companyId())
                .eq(PurchaseOrderEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseOrderEntity::getDeletedFlag, 0)
                .eq(PurchaseOrderEntity::getOrderDate, today);
    }

    private LambdaQueryWrapper<SalesOrderEntity> todaySalesOrderQuery(AuditMetadata audit, LocalDate today) {
        return new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getCompanyId, audit.companyId())
                .eq(SalesOrderEntity::getAccountBookId, audit.accountBookId())
                .eq(SalesOrderEntity::getDeletedFlag, 0)
                .eq(SalesOrderEntity::getOrderDate, today);
    }

    private LambdaQueryWrapper<OperationLogEntity> failedOperationQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getCompanyId, audit.companyId())
                .eq(OperationLogEntity::getAccountBookId, audit.accountBookId())
                .in(OperationLogEntity::getResult, "FAILURE", "FAIL");
    }
}

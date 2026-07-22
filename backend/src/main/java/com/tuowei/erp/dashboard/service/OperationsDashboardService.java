package com.tuowei.erp.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.dashboard.web.OperationsDashboardFailedOperationResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardLowStockResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardSummaryResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardTodoResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardTopSkuResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class OperationsDashboardService {

    private static final int TODO_LIMIT = 12;
    private static final int PREVIEW_LIMIT = 5;
    private static final String TASK_PENDING = "PENDING";

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
    private final MessageSource messageSource;

    public OperationsDashboardService(
            AuditMetadataFactory auditMetadataFactory,
            WorkflowTaskMapper workflowTaskMapper,
            InventoryAlertService inventoryAlertService,
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            OperationLogMapper operationLogMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            Clock clock,
            MessageSource messageSource
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
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public OperationsDashboardResponse getOperationsDashboard() {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDate today = LocalDate.now(clock);
        LocalDateTime generatedAt = LocalDateTime.now(clock);

        long pendingApprovalCount = workflowTaskMapper.selectCount(pendingWorkflowQuery(audit));
        long overdueApprovalCount = workflowTaskMapper.selectCount(overdueWorkflowQuery(audit, generatedAt));
        List<WorkflowTaskEntity> workflowTasks = workflowTaskMapper.selectList(pendingWorkflowQuery(audit)
                .orderByAsc(WorkflowTaskEntity::getCreatedTime)
                .last("limit " + TODO_LIMIT));

        List<InventoryLowStockResponse> lowStock = inventoryAlertService.listLowStock(null, null);
        List<OperationsDashboardLowStockResponse> lowStockPreview = lowStock.stream()
                .limit(PREVIEW_LIMIT)
                .map(this::toLowStockResponse)
                .toList();

        LambdaQueryWrapper<ReceivableEntity> openReceivableQuery = openReceivableQuery(audit);
        long openReceivableCount = receivableMapper.selectCount(openReceivableQuery);
        List<ReceivableEntity> openReceivables = receivableMapper.selectList(openReceivableQuery(audit));
        List<ReceivableEntity> overdueReceivables = openReceivables.stream()
                .filter(receivable -> receivable.getBizDate() != null && receivable.getBizDate().isBefore(today))
                .sorted(Comparator.comparing(ReceivableEntity::getBizDate))
                .limit(PREVIEW_LIMIT)
                .toList();
        BigDecimal openReceivableAmount = sumReceivables(openReceivables);

        LambdaQueryWrapper<PayableEntity> openPayableQuery = openPayableQuery(audit);
        long openPayableCount = payableMapper.selectCount(openPayableQuery);
        List<PayableEntity> openPayables = payableMapper.selectList(openPayableQuery(audit));
        List<PayableEntity> overduePayables = openPayables.stream()
                .filter(payable -> payable.getBizDate() != null && payable.getBizDate().isBefore(today))
                .sorted(Comparator.comparing(PayableEntity::getBizDate))
                .limit(PREVIEW_LIMIT)
                .toList();
        BigDecimal openPayableAmount = sumPayables(openPayables);

        long todayPurchaseOrders = purchaseOrderMapper.selectCount(todayPurchaseOrderQuery(audit, today));
        List<SalesOrderEntity> todaySales = salesOrderMapper.selectList(todaySalesOrderQuery(audit, today));
        BigDecimal todaySalesAmount = todaySales.stream()
                .map(SalesOrderEntity::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OperationLogEntity> failedOperations = operationLogMapper.selectList(failedOperationQuery(audit)
                .orderByDesc(OperationLogEntity::getOperationTime)
                .last("limit " + PREVIEW_LIMIT));
        List<OperationsDashboardFailedOperationResponse> failedOperationPreview = failedOperations.stream()
                .map(this::toFailedOperationResponse)
                .toList();
        List<OperationsDashboardTopSkuResponse> topSkus = salesDeliveryLineMapper.selectTopSkus(
                audit.companyId(), audit.accountBookId(), today.minusDays(29), today, PREVIEW_LIMIT);

        List<OperationsDashboardTodoResponse> todos = Stream.of(
                        workflowTasks.stream().map(this::workflowTodo),
                        lowStock.stream().map(lowStockItem -> lowStockTodo(lowStockItem, generatedAt)),
                        overdueReceivables.stream().map(this::receivableTodo),
                        overduePayables.stream().map(this::payableTodo),
                        failedOperations.stream().map(this::failedOperationTodo)
                )
                .flatMap(stream -> stream)
                .sorted(Comparator
                        .comparingInt((OperationsDashboardTodoResponse todo) -> priorityRank(todo.priority()))
                        .thenComparing(OperationsDashboardTodoResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TODO_LIMIT)
                .toList();

        return new OperationsDashboardResponse(
                new OperationsDashboardSummaryResponse(
                        pendingApprovalCount,
                        overdueApprovalCount,
                        lowStock.size(),
                        openReceivableCount,
                        ScalePrecision.amount(openReceivableAmount),
                        openPayableCount,
                        ScalePrecision.amount(openPayableAmount),
                        todayPurchaseOrders,
                        ScalePrecision.amount(todaySalesAmount)
                ),
                todos,
                lowStockPreview,
                failedOperationPreview,
                topSkus,
                generatedAt
        );
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> pendingWorkflowQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getCompanyId, audit.companyId())
                .eq(WorkflowTaskEntity::getAccountBookId, audit.accountBookId())
                .eq(WorkflowTaskEntity::getApproverUserId, audit.userId())
                .eq(WorkflowTaskEntity::getStatus, TASK_PENDING);
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

    private OperationsDashboardLowStockResponse toLowStockResponse(InventoryLowStockResponse response) {
        return new OperationsDashboardLowStockResponse(
                response.ruleId(),
                response.warehouseId(),
                response.productId(),
                response.qtyOnHand(),
                response.minQty(),
                response.shortageQty(),
                response.remark()
        );
    }

    private OperationsDashboardFailedOperationResponse toFailedOperationResponse(OperationLogEntity entity) {
        return new OperationsDashboardFailedOperationResponse(
                entity.getId(),
                entity.getModule(),
                entity.getOperation(),
                entity.getBizNo(),
                entity.getMessage(),
                entity.getRequestUri(),
                entity.getOperationTime()
        );
    }

    private OperationsDashboardTodoResponse workflowTodo(WorkflowTaskEntity task) {
        String reference = defaultText(task.getBusinessNo(), businessTypeLabel(task.getBusinessType()));
        return new OperationsDashboardTodoResponse(
                "workflow-" + task.getId(),
                "WORKFLOW",
                "-".equals(reference)
                        ? message("dashboard.todo.workflow.pending", "审批待处理")
                        : message("dashboard.todo.workflow.title", "待审批：{0}", reference),
                businessTypeLabel(task.getBusinessType()),
                "HIGH",
                workflowTaskRoute(task),
                task.getCreatedTime()
        );
    }

    private String workflowTaskRoute(WorkflowTaskEntity task) {
        StringBuilder route = new StringBuilder("/workflow/tasks?");
        boolean hasParam = false;
        if (task.getBusinessType() != null && !task.getBusinessType().isBlank()) {
            route.append("businessType=").append(task.getBusinessType());
            hasParam = true;
        }
        if (task.getBusinessId() != null) {
            if (hasParam) {
                route.append("&");
            }
            route.append("businessId=").append(task.getBusinessId());
            hasParam = true;
        }
        if (hasParam) {
            route.append("&");
        }
        route.append("status=PENDING");
        return route.toString();
    }

    private OperationsDashboardTodoResponse lowStockTodo(InventoryLowStockResponse lowStock, LocalDateTime generatedAt) {
        return new OperationsDashboardTodoResponse(
                "low-stock-" + lowStock.ruleId(),
                "LOW_STOCK",
                message("dashboard.todo.lowStock.title", "库存低于预警：商品 {0}", lowStock.productId()),
                message("dashboard.todo.lowStock.description", "仓库 {0} 缺口 {1}", lowStock.warehouseId(), lowStock.shortageQty()),
                "MEDIUM",
                "/inventory/alerts",
                generatedAt
        );
    }

    private OperationsDashboardTodoResponse receivableTodo(ReceivableEntity receivable) {
        return new OperationsDashboardTodoResponse(
                "receivable-" + receivable.getId(),
                "RECEIVABLE_OVERDUE",
                message(
                        "dashboard.todo.receivable.title",
                        "应收逾期：{0}",
                        defaultText(receivable.getReceivableNo(), receivable.getSourceNo())
                ),
                message(
                        "dashboard.todo.receivable.description",
                        "业务日期 {0}，未结金额 {1}",
                        defaultText(receivable.getBizDate() == null ? null : receivable.getBizDate().toString(), "-"),
                        remaining(receivable.getOriginalAmount(), receivable.getSettledAmount()).toPlainString()
                ),
                "HIGH",
                "/finance/receivables",
                receivable.getBizDate() == null ? null : receivable.getBizDate().atStartOfDay()
        );
    }

    private OperationsDashboardTodoResponse payableTodo(PayableEntity payable) {
        return new OperationsDashboardTodoResponse(
                "payable-" + payable.getId(),
                "PAYABLE_OVERDUE",
                message(
                        "dashboard.todo.payable.title",
                        "应付逾期：{0}",
                        defaultText(payable.getPayableNo(), payable.getSourceNo())
                ),
                message(
                        "dashboard.todo.payable.description",
                        "业务日期 {0}，未结金额 {1}",
                        defaultText(payable.getBizDate() == null ? null : payable.getBizDate().toString(), "-"),
                        remaining(payable.getOriginalAmount(), payable.getSettledAmount()).toPlainString()
                ),
                "HIGH",
                "/finance/payables",
                payable.getBizDate() == null ? null : payable.getBizDate().atStartOfDay()
        );
    }

    private OperationsDashboardTodoResponse failedOperationTodo(OperationLogEntity operationLog) {
        return new OperationsDashboardTodoResponse(
                "failed-operation-" + operationLog.getId(),
                "FAILED_OPERATION",
                message(
                        "dashboard.todo.failed.title",
                        "操作失败：{0}",
                        defaultText(operationLog.getBizNo(), operationLog.getOperation())
                ),
                defaultText(
                        operationLog.getRequestUri(),
                        defaultText(operationLog.getModule(), operationLog.getOperation())
                ),
                "MEDIUM",
                "/system/logs",
                operationLog.getOperationTime()
        );
    }

    private BigDecimal sumReceivables(List<ReceivableEntity> receivables) {
        return receivables.stream()
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPayables(List<PayableEntity> payables) {
        return payables.stream()
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private String defaultText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? "-" : fallback;
    }

    private String businessTypeLabel(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            return "-";
        }
        return switch (businessType) {
            case "PURCHASE_ORDER" -> message("dashboard.todo.businessType.purchaseOrder", "采购订单");
            case "SALES_ORDER" -> message("dashboard.todo.businessType.salesOrder", "销售订单");
            case "EXPENSE" -> message("dashboard.todo.businessType.expense", "费用单");
            default -> businessType;
        };
    }

    private String message(String code, String fallback, Object... args) {
        if (messageSource == null) {
            return args == null || args.length == 0 ? fallback : MessageFormat.format(fallback, args);
        }
        return messageSource.getMessage(code, args, fallback, LocaleContextHolder.getLocale());
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}

package com.tuowei.erp.dashboard.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.dashboard.web.OperationsDashboardFailedOperationResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardLowStockResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardSummaryResponse;
import com.tuowei.erp.dashboard.web.OperationsDashboardTodoResponse;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Pure dashboard response assembly and localized todo presentation. */
@Service
public class OperationsDashboardPresentationService {

    private static final int TODO_LIMIT = 12;
    private static final int PREVIEW_LIMIT = 5;

    private final MessageSource messageSource;

    public OperationsDashboardPresentationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public OperationsDashboardResponse present(OperationsDashboardSnapshot snapshot) {
        LocalDate today = snapshot.generatedAt().toLocalDate();
        List<OperationsDashboardLowStockResponse> lowStockPreview = snapshot.lowStock().stream()
                .limit(PREVIEW_LIMIT)
                .map(this::toLowStockResponse)
                .toList();
        List<ReceivableEntity> overdueReceivables = snapshot.openReceivables().stream()
                .filter(receivable -> receivable.getBizDate() != null && receivable.getBizDate().isBefore(today))
                .sorted(Comparator.comparing(ReceivableEntity::getBizDate))
                .limit(PREVIEW_LIMIT)
                .toList();
        List<PayableEntity> overduePayables = snapshot.openPayables().stream()
                .filter(payable -> payable.getBizDate() != null && payable.getBizDate().isBefore(today))
                .sorted(Comparator.comparing(PayableEntity::getBizDate))
                .limit(PREVIEW_LIMIT)
                .toList();
        List<OperationsDashboardFailedOperationResponse> failedOperationPreview = snapshot.failedOperations().stream()
                .map(this::toFailedOperationResponse)
                .toList();

        List<OperationsDashboardTodoResponse> todos = Stream.of(
                        snapshot.workflowTasks().stream().map(this::workflowTodo),
                        snapshot.lowStock().stream().map(lowStock -> lowStockTodo(lowStock, snapshot.generatedAt())),
                        overdueReceivables.stream().map(this::receivableTodo),
                        overduePayables.stream().map(this::payableTodo),
                        snapshot.failedOperations().stream().map(this::failedOperationTodo)
                )
                .flatMap(stream -> stream)
                .sorted(Comparator
                        .comparingInt((OperationsDashboardTodoResponse todo) -> priorityRank(todo.priority()))
                        .thenComparing(OperationsDashboardTodoResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TODO_LIMIT)
                .toList();

        BigDecimal todaySalesAmount = snapshot.todaySales().stream()
                .map(entity -> entity.getTotalAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OperationsDashboardResponse(
                new OperationsDashboardSummaryResponse(
                        snapshot.pendingApprovalCount(),
                        snapshot.overdueApprovalCount(),
                        snapshot.lowStock().size(),
                        snapshot.openReceivableCount(),
                        ScalePrecision.amount(sumReceivables(snapshot.openReceivables())),
                        snapshot.openPayableCount(),
                        ScalePrecision.amount(sumPayables(snapshot.openPayables())),
                        snapshot.todayPurchaseOrders(),
                        ScalePrecision.amount(todaySalesAmount)
                ),
                todos,
                lowStockPreview,
                failedOperationPreview,
                snapshot.topSkus(),
                snapshot.generatedAt()
        );
    }

    private OperationsDashboardLowStockResponse toLowStockResponse(InventoryLowStockResponse response) {
        return new OperationsDashboardLowStockResponse(
                response.ruleId(), response.warehouseId(), response.productId(), response.qtyOnHand(),
                response.minQty(), response.shortageQty(), response.remark()
        );
    }

    private OperationsDashboardFailedOperationResponse toFailedOperationResponse(OperationLogEntity entity) {
        return new OperationsDashboardFailedOperationResponse(
                entity.getId(), entity.getModule(), entity.getOperation(), entity.getBizNo(), entity.getMessage(),
                entity.getRequestUri(), entity.getOperationTime()
        );
    }

    private OperationsDashboardTodoResponse workflowTodo(WorkflowTaskEntity task) {
        String reference = defaultText(task.getBusinessNo(), businessTypeLabel(task.getBusinessType()));
        return new OperationsDashboardTodoResponse(
                "workflow-" + task.getId(), "WORKFLOW",
                "-".equals(reference)
                        ? message("dashboard.todo.workflow.pending", "审批待处理")
                        : message("dashboard.todo.workflow.title", "待审批：{0}", reference),
                businessTypeLabel(task.getBusinessType()), "HIGH", workflowTaskRoute(task), task.getCreatedTime()
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
            if (hasParam) route.append("&");
            route.append("businessId=").append(task.getBusinessId());
            hasParam = true;
        }
        if (hasParam) route.append("&");
        route.append("status=PENDING");
        return route.toString();
    }

    private OperationsDashboardTodoResponse lowStockTodo(InventoryLowStockResponse lowStock, java.time.LocalDateTime generatedAt) {
        return new OperationsDashboardTodoResponse(
                "low-stock-" + lowStock.ruleId(), "LOW_STOCK",
                message("dashboard.todo.lowStock.title", "库存低于预警：商品 {0}", lowStock.productId()),
                message("dashboard.todo.lowStock.description", "仓库 {0} 缺口 {1}", lowStock.warehouseId(), lowStock.shortageQty()),
                "MEDIUM", "/inventory/alerts", generatedAt
        );
    }

    private OperationsDashboardTodoResponse receivableTodo(ReceivableEntity receivable) {
        return new OperationsDashboardTodoResponse(
                "receivable-" + receivable.getId(), "RECEIVABLE_OVERDUE",
                message("dashboard.todo.receivable.title", "应收逾期：{0}", defaultText(receivable.getReceivableNo(), receivable.getSourceNo())),
                message("dashboard.todo.receivable.description", "业务日期 {0}，未结金额 {1}",
                        defaultText(receivable.getBizDate() == null ? null : receivable.getBizDate().toString(), "-"),
                        remaining(receivable.getOriginalAmount(), receivable.getSettledAmount()).toPlainString()),
                "HIGH", "/finance/receivables",
                receivable.getBizDate() == null ? null : receivable.getBizDate().atStartOfDay()
        );
    }

    private OperationsDashboardTodoResponse payableTodo(PayableEntity payable) {
        return new OperationsDashboardTodoResponse(
                "payable-" + payable.getId(), "PAYABLE_OVERDUE",
                message("dashboard.todo.payable.title", "应付逾期：{0}", defaultText(payable.getPayableNo(), payable.getSourceNo())),
                message("dashboard.todo.payable.description", "业务日期 {0}，未结金额 {1}",
                        defaultText(payable.getBizDate() == null ? null : payable.getBizDate().toString(), "-"),
                        remaining(payable.getOriginalAmount(), payable.getSettledAmount()).toPlainString()),
                "HIGH", "/finance/payables",
                payable.getBizDate() == null ? null : payable.getBizDate().atStartOfDay()
        );
    }

    private OperationsDashboardTodoResponse failedOperationTodo(OperationLogEntity operationLog) {
        return new OperationsDashboardTodoResponse(
                "failed-operation-" + operationLog.getId(), "FAILED_OPERATION",
                message("dashboard.todo.failed.title", "操作失败：{0}", defaultText(operationLog.getBizNo(), operationLog.getOperation())),
                defaultText(operationLog.getRequestUri(), defaultText(operationLog.getModule(), operationLog.getOperation())),
                "MEDIUM", "/system/logs", operationLog.getOperationTime()
        );
    }

    private BigDecimal sumReceivables(List<ReceivableEntity> receivables) {
        return receivables.stream().map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPayables(List<PayableEntity> payables) {
        return payables.stream().map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount)
                .subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private String defaultText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback == null || fallback.isBlank() ? "-" : fallback;
    }

    private String businessTypeLabel(String businessType) {
        if (!StringUtils.hasText(businessType)) return "-";
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

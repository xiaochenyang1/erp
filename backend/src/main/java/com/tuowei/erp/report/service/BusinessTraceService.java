package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.report.web.BusinessTraceDocumentResponse;
import com.tuowei.erp.report.web.BusinessTraceExceptionTicketResponse;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import com.tuowei.erp.report.web.BusinessTraceResponse;
import com.tuowei.erp.report.web.BusinessTraceSummaryResponse;
import com.tuowei.erp.report.web.BusinessTraceTimelineResponse;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BusinessTraceService {

    private static final int SOURCE_LIMIT = 20;
    private static final Set<String> CLOSED_STATUSES = Set.of("SETTLED", "CANCELLED", "CLOSED");

    private final CurrentUserContext currentUserContext;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final OperationLogMapper operationLogMapper;
    private final ExceptionTicketMapper exceptionTicketMapper;
    private final Clock clock;

    public BusinessTraceService(
            CurrentUserContext currentUserContext,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            WorkflowTaskMapper workflowTaskMapper,
            OperationLogMapper operationLogMapper,
            ExceptionTicketMapper exceptionTicketMapper,
            Clock clock
    ) {
        this.currentUserContext = currentUserContext;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        this.operationLogMapper = operationLogMapper;
        this.exceptionTicketMapper = exceptionTicketMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessTraceResponse trace(BusinessTraceQuery query) {
        String keyword = normalizeKeyword(query);
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        if (!StringUtils.hasText(keyword)) {
            return empty(keyword, generatedAt);
        }

        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        List<SalesOrderEntity> salesOrders = listSalesOrders(keyword, currentUser);
        List<PurchaseOrderEntity> purchaseOrders = listPurchaseOrders(keyword, currentUser);

        Set<Long> salesOrderIds = ids(salesOrders);
        Set<Long> purchaseOrderIds = ids(purchaseOrders);
        Set<String> knownBizNos = new LinkedHashSet<>();
        salesOrders.stream().map(SalesOrderEntity::getOrderNo).forEach(knownBizNos::add);
        purchaseOrders.stream().map(PurchaseOrderEntity::getOrderNo).forEach(knownBizNos::add);

        List<SalesDeliveryEntity> salesDeliveries = listSalesDeliveries(keyword, salesOrderIds, currentUser);
        List<PurchaseReceiptEntity> purchaseReceipts = listPurchaseReceipts(keyword, purchaseOrderIds, currentUser);
        salesDeliveries.stream().map(SalesDeliveryEntity::getDeliveryNo).forEach(knownBizNos::add);
        purchaseReceipts.stream().map(PurchaseReceiptEntity::getReceiptNo).forEach(knownBizNos::add);

        List<ReceivableEntity> receivables = listReceivables(keyword, knownBizNos, currentUser);
        List<PayableEntity> payables = listPayables(keyword, knownBizNos, currentUser);
        receivables.stream().map(ReceivableEntity::getReceivableNo).forEach(knownBizNos::add);
        payables.stream().map(PayableEntity::getPayableNo).forEach(knownBizNos::add);

        List<InventoryTransactionEntity> inventoryTransactions = listInventoryTransactions(keyword, knownBizNos, currentUser);
        inventoryTransactions.stream().map(InventoryTransactionEntity::getBizNo).forEach(knownBizNos::add);

        List<WorkflowTaskEntity> workflowTasks = listWorkflowTasks(keyword, knownBizNos, currentUser);
        List<OperationLogEntity> operationLogs = listOperationLogs(keyword, knownBizNos, currentUser);
        List<ExceptionTicketEntity> exceptionTickets = listExceptionTickets(keyword, knownBizNos, currentUser);

        List<BusinessTraceDocumentResponse> documents = new ArrayList<>();
        salesOrders.stream().map(this::salesOrderDocument).forEach(documents::add);
        purchaseOrders.stream().map(this::purchaseOrderDocument).forEach(documents::add);
        salesDeliveries.stream().map(this::salesDeliveryDocument).forEach(documents::add);
        purchaseReceipts.stream().map(this::purchaseReceiptDocument).forEach(documents::add);
        receivables.stream().map(this::receivableDocument).forEach(documents::add);
        payables.stream().map(this::payableDocument).forEach(documents::add);

        List<BusinessTraceTimelineResponse> timeline = new ArrayList<>();
        salesOrders.stream().map(this::salesOrderTimeline).forEach(timeline::add);
        purchaseOrders.stream().map(this::purchaseOrderTimeline).forEach(timeline::add);
        salesDeliveries.stream().map(this::salesDeliveryTimeline).forEach(timeline::add);
        purchaseReceipts.stream().map(this::purchaseReceiptTimeline).forEach(timeline::add);
        receivables.stream().map(this::receivableTimeline).forEach(timeline::add);
        payables.stream().map(this::payableTimeline).forEach(timeline::add);
        inventoryTransactions.stream().map(this::inventoryTimeline).forEach(timeline::add);
        workflowTasks.stream().map(this::workflowTimeline).forEach(timeline::add);
        operationLogs.stream().map(this::operationLogTimeline).forEach(timeline::add);
        timeline.sort(Comparator.comparing(
                BusinessTraceTimelineResponse::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        return new BusinessTraceResponse(
                keyword,
                documents,
                timeline,
                exceptionTickets.stream().map(this::exceptionTicketResponse).toList(),
                summary(documents, timeline, receivables, payables, inventoryTransactions, operationLogs, exceptionTickets),
                generatedAt
        );
    }

    private List<SalesOrderEntity> listSalesOrders(String keyword, CurrentUser currentUser) {
        return salesOrderMapper.selectList(salesOrderWrapper(currentUser)
                .eq(SalesOrderEntity::getDeletedFlag, 0)
                .like(SalesOrderEntity::getOrderNo, keyword)
                .orderByDesc(SalesOrderEntity::getOrderDate)
                .orderByDesc(SalesOrderEntity::getId)
                .last(limitSql()));
    }

    private List<PurchaseOrderEntity> listPurchaseOrders(String keyword, CurrentUser currentUser) {
        return purchaseOrderMapper.selectList(purchaseOrderWrapper(currentUser)
                .eq(PurchaseOrderEntity::getDeletedFlag, 0)
                .like(PurchaseOrderEntity::getOrderNo, keyword)
                .orderByDesc(PurchaseOrderEntity::getOrderDate)
                .orderByDesc(PurchaseOrderEntity::getId)
                .last(limitSql()));
    }

    private List<SalesDeliveryEntity> listSalesDeliveries(String keyword, Set<Long> orderIds, CurrentUser currentUser) {
        LambdaQueryWrapper<SalesDeliveryEntity> wrapper = salesDeliveryWrapper(currentUser)
                .eq(SalesDeliveryEntity::getDeletedFlag, 0);
        if (orderIds.isEmpty()) {
            wrapper.like(SalesDeliveryEntity::getDeliveryNo, keyword);
        } else {
            wrapper.and(nested -> nested.like(SalesDeliveryEntity::getDeliveryNo, keyword)
                    .or()
                    .in(SalesDeliveryEntity::getOrderId, orderIds));
        }
        return salesDeliveryMapper.selectList(wrapper
                .orderByDesc(SalesDeliveryEntity::getDeliveryDate)
                .orderByDesc(SalesDeliveryEntity::getId)
                .last(limitSql()));
    }

    private List<PurchaseReceiptEntity> listPurchaseReceipts(String keyword, Set<Long> orderIds, CurrentUser currentUser) {
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = purchaseReceiptWrapper(currentUser)
                .eq(PurchaseReceiptEntity::getDeletedFlag, 0);
        if (orderIds.isEmpty()) {
            wrapper.like(PurchaseReceiptEntity::getReceiptNo, keyword);
        } else {
            wrapper.and(nested -> nested.like(PurchaseReceiptEntity::getReceiptNo, keyword)
                    .or()
                    .in(PurchaseReceiptEntity::getOrderId, orderIds));
        }
        return purchaseReceiptMapper.selectList(wrapper
                .orderByDesc(PurchaseReceiptEntity::getReceiptDate)
                .orderByDesc(PurchaseReceiptEntity::getId)
                .last(limitSql()));
    }

    private List<ReceivableEntity> listReceivables(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = receivableWrapper(currentUser)
                .eq(ReceivableEntity::getDeletedFlag, 0);
        wrapper.and(nested -> {
            nested.like(ReceivableEntity::getReceivableNo, keyword)
                    .or()
                    .like(ReceivableEntity::getSourceNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(ReceivableEntity::getSourceNo, knownBizNos);
            }
        });
        return receivableMapper.selectList(wrapper
                .orderByDesc(ReceivableEntity::getBizDate)
                .orderByDesc(ReceivableEntity::getId)
                .last(limitSql()));
    }

    private List<PayableEntity> listPayables(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<PayableEntity> wrapper = payableWrapper(currentUser)
                .eq(PayableEntity::getDeletedFlag, 0);
        wrapper.and(nested -> {
            nested.like(PayableEntity::getPayableNo, keyword)
                    .or()
                    .like(PayableEntity::getSourceNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(PayableEntity::getSourceNo, knownBizNos);
            }
        });
        return payableMapper.selectList(wrapper
                .orderByDesc(PayableEntity::getBizDate)
                .orderByDesc(PayableEntity::getId)
                .last(limitSql()));
    }

    private List<InventoryTransactionEntity> listInventoryTransactions(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = inventoryTransactionWrapper(currentUser);
        wrapper.and(nested -> {
            nested.like(InventoryTransactionEntity::getBizNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(InventoryTransactionEntity::getBizNo, knownBizNos);
            }
        });
        return inventoryTransactionMapper.selectList(wrapper
                .orderByDesc(InventoryTransactionEntity::getOccurredTime)
                .orderByDesc(InventoryTransactionEntity::getId)
                .last(limitSql()));
    }

    private List<WorkflowTaskEntity> listWorkflowTasks(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<WorkflowTaskEntity> wrapper = workflowTaskWrapper(currentUser);
        wrapper.and(nested -> {
            nested.like(WorkflowTaskEntity::getBusinessNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(WorkflowTaskEntity::getBusinessNo, knownBizNos);
            }
        });
        return workflowTaskMapper.selectList(wrapper
                .orderByDesc(WorkflowTaskEntity::getCreatedTime)
                .orderByDesc(WorkflowTaskEntity::getId)
                .last(limitSql()));
    }

    private List<OperationLogEntity> listOperationLogs(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<OperationLogEntity> wrapper = operationLogWrapper(currentUser);
        wrapper.and(nested -> {
            nested.like(OperationLogEntity::getBizNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(OperationLogEntity::getBizNo, knownBizNos);
            }
        });
        return operationLogMapper.selectList(wrapper
                .orderByDesc(OperationLogEntity::getOperationTime)
                .orderByDesc(OperationLogEntity::getId)
                .last(limitSql()));
    }

    private List<ExceptionTicketEntity> listExceptionTickets(String keyword, Set<String> knownBizNos, CurrentUser currentUser) {
        LambdaQueryWrapper<ExceptionTicketEntity> wrapper = exceptionTicketWrapper(currentUser)
                .eq(ExceptionTicketEntity::getDeletedFlag, 0);
        wrapper.and(nested -> {
            nested.like(ExceptionTicketEntity::getTicketNo, keyword)
                    .or()
                    .like(ExceptionTicketEntity::getSourceNo, keyword);
            if (!knownBizNos.isEmpty()) {
                nested.or().in(ExceptionTicketEntity::getSourceNo, knownBizNos);
            }
        });
        return exceptionTicketMapper.selectList(wrapper
                .orderByDesc(ExceptionTicketEntity::getUpdatedTime)
                .orderByDesc(ExceptionTicketEntity::getId)
                .last(limitSql()));
    }

    private BusinessTraceDocumentResponse salesOrderDocument(SalesOrderEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("SALES_ORDER", entity.getId()),
                "SALES_ORDER",
                "销售订单",
                entity.getId(),
                entity.getOrderNo(),
                "客户 " + entity.getCustomerId(),
                entity.getStatus(),
                entity.getDeliveryStatus(),
                entity.getOrderDate(),
                "CUSTOMER",
                entity.getCustomerId(),
                ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getTotalAmount())),
                route("/sales/orders", entity.getOrderNo())
        );
    }

    private BusinessTraceDocumentResponse purchaseOrderDocument(PurchaseOrderEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("PURCHASE_ORDER", entity.getId()),
                "PURCHASE_ORDER",
                "采购订单",
                entity.getId(),
                entity.getOrderNo(),
                "供应商 " + entity.getSupplierId(),
                entity.getStatus(),
                entity.getReceiptStatus(),
                entity.getOrderDate(),
                "SUPPLIER",
                entity.getSupplierId(),
                ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getTotalAmount())),
                route("/purchase/orders", entity.getOrderNo())
        );
    }

    private BusinessTraceDocumentResponse salesDeliveryDocument(SalesDeliveryEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("SALES_DELIVERY", entity.getId()),
                "SALES_DELIVERY",
                "销售发货",
                entity.getId(),
                entity.getDeliveryNo(),
                "销售订单ID " + entity.getOrderId(),
                entity.getStatus(),
                null,
                entity.getDeliveryDate(),
                "ORDER",
                entity.getOrderId(),
                ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getTotalAmount())),
                route("/sales/deliveries", entity.getDeliveryNo())
        );
    }

    private BusinessTraceDocumentResponse purchaseReceiptDocument(PurchaseReceiptEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("PURCHASE_RECEIPT", entity.getId()),
                "PURCHASE_RECEIPT",
                "采购收货",
                entity.getId(),
                entity.getReceiptNo(),
                "采购订单ID " + entity.getOrderId(),
                entity.getStatus(),
                null,
                entity.getReceiptDate(),
                "ORDER",
                entity.getOrderId(),
                ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                ScalePrecision.amount(ScalePrecision.zeroDefault(entity.getTotalAmount())),
                route("/purchase/receipts", entity.getReceiptNo())
        );
    }

    private BusinessTraceDocumentResponse receivableDocument(ReceivableEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("RECEIVABLE", entity.getId()),
                "RECEIVABLE",
                "应收账款",
                entity.getId(),
                entity.getReceivableNo(),
                "来源 " + entity.getSourceNo(),
                entity.getStatus(),
                entity.getSourceType(),
                entity.getBizDate(),
                "CUSTOMER",
                entity.getCustomerId(),
                null,
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                route("/finance/receivables", entity.getReceivableNo())
        );
    }

    private BusinessTraceDocumentResponse payableDocument(PayableEntity entity) {
        return new BusinessTraceDocumentResponse(
                documentId("PAYABLE", entity.getId()),
                "PAYABLE",
                "应付账款",
                entity.getId(),
                entity.getPayableNo(),
                "来源 " + entity.getSourceNo(),
                entity.getStatus(),
                entity.getSourceType(),
                entity.getBizDate(),
                "SUPPLIER",
                entity.getSupplierId(),
                null,
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                route("/finance/payables", entity.getPayableNo())
        );
    }

    private BusinessTraceTimelineResponse salesOrderTimeline(SalesOrderEntity entity) {
        return timeline(
                "ORDER",
                "销售订单创建",
                entity.getOrderNo(),
                "销售订单 " + nullSafe(entity.getStatus()) + "，审批 " + nullSafe(entity.getApprovalStatus()),
                occurredAt(entity.getCreatedTime(), entity.getOrderDate()),
                entity.getStatus(),
                "NORMAL",
                route("/sales/orders", entity.getOrderNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse purchaseOrderTimeline(PurchaseOrderEntity entity) {
        return timeline(
                "ORDER",
                "采购订单创建",
                entity.getOrderNo(),
                "采购订单 " + nullSafe(entity.getStatus()) + "，审批 " + nullSafe(entity.getApprovalStatus()),
                occurredAt(entity.getCreatedTime(), entity.getOrderDate()),
                entity.getStatus(),
                "NORMAL",
                route("/purchase/orders", entity.getOrderNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse salesDeliveryTimeline(SalesDeliveryEntity entity) {
        return timeline(
                "FULFILLMENT",
                "销售发货",
                entity.getDeliveryNo(),
                "发货数量 " + ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                occurredAt(entity.getCreatedTime(), entity.getDeliveryDate()),
                entity.getStatus(),
                "NORMAL",
                route("/sales/deliveries", entity.getDeliveryNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse purchaseReceiptTimeline(PurchaseReceiptEntity entity) {
        return timeline(
                "FULFILLMENT",
                "采购收货",
                entity.getReceiptNo(),
                "收货数量 " + ScalePrecision.safeQuantity(entity.getTotalQuantity()),
                occurredAt(entity.getCreatedTime(), entity.getReceiptDate()),
                entity.getStatus(),
                "NORMAL",
                route("/purchase/receipts", entity.getReceiptNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse receivableTimeline(ReceivableEntity entity) {
        return timeline(
                "FINANCE",
                "应收账款",
                entity.getReceivableNo(),
                "来源 " + nullSafe(entity.getSourceNo()) + "，未结 " + remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                occurredAt(entity.getCreatedTime(), entity.getBizDate()),
                entity.getStatus(),
                isOpen(entity.getStatus()) ? "WARNING" : "NORMAL",
                route("/finance/receivables", entity.getReceivableNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse payableTimeline(PayableEntity entity) {
        return timeline(
                "FINANCE",
                "应付账款",
                entity.getPayableNo(),
                "来源 " + nullSafe(entity.getSourceNo()) + "，未结 " + remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                occurredAt(entity.getCreatedTime(), entity.getBizDate()),
                entity.getStatus(),
                isOpen(entity.getStatus()) ? "WARNING" : "NORMAL",
                route("/finance/payables", entity.getPayableNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse inventoryTimeline(InventoryTransactionEntity entity) {
        return timeline(
                "INVENTORY",
                "库存流水",
                entity.getBizNo(),
                nullSafe(entity.getDirection()) + " " + ScalePrecision.safeQuantity(entity.getQty()) + "，产品 " + entity.getProductId(),
                entity.getOccurredTime(),
                entity.getDirection(),
                "NORMAL",
                "/reports?tab=inventoryTransaction&bizNo=" + encode(entity.getBizNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse workflowTimeline(WorkflowTaskEntity entity) {
        return timeline(
                "WORKFLOW",
                "审批任务",
                entity.getBusinessNo(),
                nullSafe(entity.getTitle()),
                entity.getCreatedTime(),
                entity.getStatus(),
                "PENDING".equalsIgnoreCase(nullSafe(entity.getStatus())) ? "WARNING" : "NORMAL",
                route("/workflow/tasks", entity.getBusinessNo()),
                entity.getId()
        );
    }

    private BusinessTraceTimelineResponse operationLogTimeline(OperationLogEntity entity) {
        return timeline(
                "OPERATION_LOG",
                "操作日志",
                entity.getBizNo(),
                nullSafe(entity.getModule()) + " / " + nullSafe(entity.getOperation()) + " / " + nullSafe(entity.getMessage()),
                entity.getOperationTime(),
                entity.getResult(),
                "FAILURE".equalsIgnoreCase(nullSafe(entity.getResult())) ? "ERROR" : "NORMAL",
                route("/system/logs", entity.getBizNo()),
                entity.getId()
        );
    }

    private BusinessTraceExceptionTicketResponse exceptionTicketResponse(ExceptionTicketEntity entity) {
        return new BusinessTraceExceptionTicketResponse(
                entity.getId(),
                entity.getTicketNo(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getTitle(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getSourceNo(),
                entity.getStatus(),
                entity.getAssigneeUserId(),
                entity.getDueTime(),
                entity.getUpdatedTime(),
                route("/exception-tickets", entity.getTicketNo())
        );
    }

    private BusinessTraceTimelineResponse timeline(
            String eventType,
            String title,
            String bizNo,
            String description,
            LocalDateTime occurredAt,
            String status,
            String severity,
            String route,
            Long sourceId
    ) {
        return new BusinessTraceTimelineResponse(
                eventType + "-" + sourceId,
                eventType,
                title,
                bizNo,
                description,
                occurredAt,
                status,
                severity,
                route
        );
    }

    private BusinessTraceSummaryResponse summary(
            List<BusinessTraceDocumentResponse> documents,
            List<BusinessTraceTimelineResponse> timeline,
            List<ReceivableEntity> receivables,
            List<PayableEntity> payables,
            List<InventoryTransactionEntity> inventoryTransactions,
            List<OperationLogEntity> operationLogs,
            List<ExceptionTicketEntity> exceptionTickets
    ) {
        BigDecimal openReceivableAmount = receivables.stream()
                .filter(entity -> isOpen(entity.getStatus()))
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openPayableAmount = payables.stream()
                .filter(entity -> isOpen(entity.getStatus()))
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inventoryMovementQuantity = inventoryTransactions.stream()
                .map(InventoryTransactionEntity::getQty)
                .map(ScalePrecision::zeroDefault)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int failedOperationCount = (int) operationLogs.stream()
                .filter(entity -> "FAILURE".equalsIgnoreCase(nullSafe(entity.getResult())))
                .count();
        int openExceptionTicketCount = (int) exceptionTickets.stream()
                .filter(entity -> !isClosedExceptionTicket(entity.getStatus()))
                .count();
        return new BusinessTraceSummaryResponse(
                documents.size(),
                timeline.size(),
                ScalePrecision.amount(openReceivableAmount),
                ScalePrecision.amount(openPayableAmount),
                ScalePrecision.quantity(inventoryMovementQuantity),
                failedOperationCount,
                openExceptionTicketCount
        );
    }

    private LambdaQueryWrapper<SalesOrderEntity> salesOrderWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getCompanyId, currentUser.companyId())
                .eq(SalesOrderEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getCompanyId, currentUser.companyId())
                .eq(PurchaseOrderEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<SalesDeliveryEntity> salesDeliveryWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<SalesDeliveryEntity>()
                .eq(SalesDeliveryEntity::getCompanyId, currentUser.companyId())
                .eq(SalesDeliveryEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<PurchaseReceiptEntity> purchaseReceiptWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getCompanyId, currentUser.companyId())
                .eq(PurchaseReceiptEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, currentUser.companyId())
                .eq(ReceivableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, currentUser.companyId())
                .eq(PayableEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<InventoryTransactionEntity> inventoryTransactionWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> workflowTaskWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getCompanyId, currentUser.companyId())
                .eq(WorkflowTaskEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<OperationLogEntity> operationLogWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getCompanyId, currentUser.companyId())
                .eq(OperationLogEntity::getAccountBookId, currentUser.accountBookId());
    }

    private LambdaQueryWrapper<ExceptionTicketEntity> exceptionTicketWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<ExceptionTicketEntity>()
                .eq(ExceptionTicketEntity::getCompanyId, currentUser.companyId())
                .eq(ExceptionTicketEntity::getAccountBookId, currentUser.accountBookId());
    }

    private Set<Long> ids(List<? extends Object> entities) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object entity : entities) {
            if (entity instanceof SalesOrderEntity salesOrder && salesOrder.getId() != null) {
                ids.add(salesOrder.getId());
            } else if (entity instanceof PurchaseOrderEntity purchaseOrder && purchaseOrder.getId() != null) {
                ids.add(purchaseOrder.getId());
            }
        }
        return ids;
    }

    private String normalizeKeyword(BusinessTraceQuery query) {
        if (query == null || !StringUtils.hasText(query.getKeyword())) {
            return "";
        }
        return query.getKeyword().trim();
    }

    private BusinessTraceResponse empty(String keyword, LocalDateTime generatedAt) {
        return new BusinessTraceResponse(
                keyword,
                List.of(),
                List.of(),
                List.of(),
                new BusinessTraceSummaryResponse(
                        0,
                        0,
                        ScalePrecision.amount(BigDecimal.ZERO),
                        ScalePrecision.amount(BigDecimal.ZERO),
                        ScalePrecision.quantity(BigDecimal.ZERO),
                        0,
                        0
                ),
                generatedAt
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private boolean isOpen(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return !CLOSED_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isClosedExceptionTicket(String status) {
        return "CLOSED".equalsIgnoreCase(nullSafe(status));
    }

    private LocalDateTime occurredAt(LocalDateTime dateTime, LocalDate date) {
        if (dateTime != null) {
            return dateTime;
        }
        return date == null ? null : date.atStartOfDay();
    }

    private String documentId(String type, Long id) {
        return type + "-" + id;
    }

    private String route(String path, String keyword) {
        return path + "?keyword=" + encode(keyword);
    }

    private String encode(String value) {
        return URLEncoder.encode(nullSafe(value), StandardCharsets.UTF_8);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String limitSql() {
        return "limit " + SOURCE_LIMIT;
    }
}

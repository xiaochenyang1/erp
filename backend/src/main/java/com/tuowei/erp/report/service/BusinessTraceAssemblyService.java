package com.tuowei.erp.report.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.report.web.BusinessTraceDocumentResponse;
import com.tuowei.erp.report.web.BusinessTraceExceptionTicketResponse;
import com.tuowei.erp.report.web.BusinessTraceResponse;
import com.tuowei.erp.report.web.BusinessTraceSummaryResponse;
import com.tuowei.erp.report.web.BusinessTraceTimelineResponse;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure document, timeline, exception and summary assembly for business trace results. */
@Service
public class BusinessTraceAssemblyService {

    private static final Set<String> CLOSED_STATUSES = Set.of("SETTLED", "CANCELLED", "CLOSED");

    public BusinessTraceResponse assemble(String keyword, LocalDateTime generatedAt, TraceData data) {
        List<BusinessTraceDocumentResponse> documents = new ArrayList<>();
        data.salesOrders().stream().map(this::salesOrderDocument).forEach(documents::add);
        data.purchaseOrders().stream().map(this::purchaseOrderDocument).forEach(documents::add);
        data.salesDeliveries().stream().map(this::salesDeliveryDocument).forEach(documents::add);
        data.purchaseReceipts().stream().map(this::purchaseReceiptDocument).forEach(documents::add);
        data.receivables().stream().map(this::receivableDocument).forEach(documents::add);
        data.payables().stream().map(this::payableDocument).forEach(documents::add);

        List<BusinessTraceTimelineResponse> timeline = new ArrayList<>();
        data.salesOrders().stream().map(this::salesOrderTimeline).forEach(timeline::add);
        data.purchaseOrders().stream().map(this::purchaseOrderTimeline).forEach(timeline::add);
        data.salesDeliveries().stream().map(this::salesDeliveryTimeline).forEach(timeline::add);
        data.purchaseReceipts().stream().map(this::purchaseReceiptTimeline).forEach(timeline::add);
        data.receivables().stream().map(this::receivableTimeline).forEach(timeline::add);
        data.payables().stream().map(this::payableTimeline).forEach(timeline::add);
        data.inventoryTransactions().stream().map(this::inventoryTimeline).forEach(timeline::add);
        data.workflowTasks().stream().map(this::workflowTimeline).forEach(timeline::add);
        data.operationLogs().stream().map(this::operationLogTimeline).forEach(timeline::add);
        timeline.sort(Comparator.comparing(
                BusinessTraceTimelineResponse::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        List<BusinessTraceExceptionTicketResponse> exceptionTickets = data.exceptionTickets().stream()
                .map(this::exceptionTicketResponse)
                .toList();
        return new BusinessTraceResponse(
                keyword,
                documents,
                timeline,
                exceptionTickets,
                summary(documents, timeline, data),
                generatedAt
        );
    }

    public BusinessTraceResponse empty(String keyword, LocalDateTime generatedAt) {
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
                "来源 " + nullSafe(entity.getSourceNo()) + "，未结 "
                        + remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
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
                "来源 " + nullSafe(entity.getSourceNo()) + "，未结 "
                        + remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
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
                nullSafe(entity.getDirection()) + " " + ScalePrecision.safeQuantity(entity.getQty())
                        + "，产品 " + entity.getProductId(),
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
                nullSafe(entity.getModule()) + " / " + nullSafe(entity.getOperation())
                        + " / " + nullSafe(entity.getMessage()),
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
            TraceData data
    ) {
        BigDecimal openReceivableAmount = data.receivables().stream()
                .filter(entity -> isOpen(entity.getStatus()))
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openPayableAmount = data.payables().stream()
                .filter(entity -> isOpen(entity.getStatus()))
                .map(entity -> remaining(entity.getOriginalAmount(), entity.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inventoryMovementQuantity = data.inventoryTransactions().stream()
                .map(InventoryTransactionEntity::getQty)
                .map(ScalePrecision::zeroDefault)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int failedOperationCount = (int) data.operationLogs().stream()
                .filter(entity -> "FAILURE".equalsIgnoreCase(nullSafe(entity.getResult())))
                .count();
        int openExceptionTicketCount = (int) data.exceptionTickets().stream()
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

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))
        );
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

    public record TraceData(
            List<SalesOrderEntity> salesOrders,
            List<PurchaseOrderEntity> purchaseOrders,
            List<SalesDeliveryEntity> salesDeliveries,
            List<PurchaseReceiptEntity> purchaseReceipts,
            List<ReceivableEntity> receivables,
            List<PayableEntity> payables,
            List<InventoryTransactionEntity> inventoryTransactions,
            List<WorkflowTaskEntity> workflowTasks,
            List<OperationLogEntity> operationLogs,
            List<ExceptionTicketEntity> exceptionTickets
    ) {
        public TraceData {
            salesOrders = List.copyOf(salesOrders);
            purchaseOrders = List.copyOf(purchaseOrders);
            salesDeliveries = List.copyOf(salesDeliveries);
            purchaseReceipts = List.copyOf(purchaseReceipts);
            receivables = List.copyOf(receivables);
            payables = List.copyOf(payables);
            inventoryTransactions = List.copyOf(inventoryTransactions);
            workflowTasks = List.copyOf(workflowTasks);
            operationLogs = List.copyOf(operationLogs);
            exceptionTickets = List.copyOf(exceptionTickets);
        }
    }
}

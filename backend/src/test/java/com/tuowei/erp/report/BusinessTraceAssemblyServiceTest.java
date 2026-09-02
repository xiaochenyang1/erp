package com.tuowei.erp.report;

import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.report.service.BusinessTraceAssemblyService;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTraceAssemblyServiceTest {

    private final BusinessTraceAssemblyService service = new BusinessTraceAssemblyService();

    @Test
    void assemblesAllDocumentAndTimelineTypesWithStableMappingAndOrdering() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 6, 30, 10, 0);

        var response = service.assemble(
                "SO /001",
                generatedAt,
                data(
                        List.of(salesOrder()),
                        List.of(purchaseOrder()),
                        List.of(salesDelivery()),
                        List.of(purchaseReceipt()),
                        List.of(receivable("OPEN", "1000.00", "300.00")),
                        List.of(payable("OPEN", "700.00", "200.00")),
                        List.of(inventoryTransaction("-5.0000", LocalDateTime.of(2026, 6, 21, 12, 0))),
                        List.of(workflowTask()),
                        List.of(operationLog("FAILURE", LocalDateTime.of(2026, 6, 24, 9, 0))),
                        List.of(exceptionTicket("OPEN"))
                )
        );

        assertThat(response.keyword()).isEqualTo("SO /001");
        assertThat(response.documents())
                .extracting("documentType")
                .containsExactly(
                        "SALES_ORDER",
                        "PURCHASE_ORDER",
                        "SALES_DELIVERY",
                        "PURCHASE_RECEIPT",
                        "RECEIVABLE",
                        "PAYABLE"
                );
        assertThat(response.documents().get(0).id()).isEqualTo("SALES_ORDER-1001");
        assertThat(response.documents().get(0).partnerType()).isEqualTo("CUSTOMER");
        assertThat(response.documents().get(0).totalQuantity()).isEqualByComparingTo("10.0000");
        assertThat(response.documents().get(0).totalAmount()).isEqualByComparingTo("1200.00");
        assertThat(response.documents().get(0).route()).isEqualTo("/sales/orders?keyword=SO+%2F001");
        assertThat(response.documents().get(4).totalAmount()).isEqualByComparingTo("700.00");
        assertThat(response.documents().get(5).totalAmount()).isEqualByComparingTo("500.00");

        assertThat(response.timeline()).hasSize(9);
        assertThat(response.timeline())
                .extracting("eventType")
                .contains("ORDER", "FULFILLMENT", "FINANCE", "INVENTORY", "WORKFLOW", "OPERATION_LOG");
        assertThat(response.timeline())
                .extracting("occurredAt")
                .containsExactly(
                        LocalDateTime.of(2026, 6, 18, 0, 0),
                        LocalDateTime.of(2026, 6, 19, 0, 0),
                        LocalDateTime.of(2026, 6, 20, 10, 0),
                        LocalDateTime.of(2026, 6, 21, 0, 0),
                        LocalDateTime.of(2026, 6, 21, 12, 0),
                        LocalDateTime.of(2026, 6, 22, 0, 0),
                        LocalDateTime.of(2026, 6, 23, 0, 0),
                        LocalDateTime.of(2026, 6, 24, 9, 0),
                        null
                );
        assertThat(response.timeline().get(response.timeline().size() - 1).id()).isEqualTo("ORDER-1001");
        assertThat(response.timeline())
                .filteredOn(item -> item.id().equals("OPERATION_LOG-9001"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.severity()).isEqualTo("ERROR");
                    assertThat(item.route()).isEqualTo("/system/logs?keyword=SO+%2F001");
                });

        assertThat(response.exceptionTickets()).singleElement().satisfies(ticket -> {
            assertThat(ticket.ticketNo()).isEqualTo("ET /001");
            assertThat(ticket.route()).isEqualTo("/exception-tickets?keyword=ET+%2F001");
        });
        assertThat(response.summary().documentCount()).isEqualTo(6);
        assertThat(response.summary().timelineCount()).isEqualTo(9);
        assertThat(response.summary().openReceivableAmount()).isEqualByComparingTo("700.00");
        assertThat(response.summary().openPayableAmount()).isEqualByComparingTo("500.00");
        assertThat(response.summary().inventoryMovementQuantity()).isEqualByComparingTo("5.0000");
        assertThat(response.summary().failedOperationCount()).isEqualTo(1);
        assertThat(response.summary().openExceptionTicketCount()).isEqualTo(1);
        assertThat(response.generatedAt()).isEqualTo(generatedAt);
    }

    @Test
    void summaryHandlesClosedStatusesNullValuesAbsoluteQuantitiesAndTicketClosureRules() {
        ReceivableEntity blankStatus = receivable(null, null, null);
        blankStatus.setId(5010L);
        blankStatus.setReceivableNo("AR-BLANK");
        ReceivableEntity open = receivable("OPEN", "10.00", null);
        open.setId(5011L);
        open.setReceivableNo("AR-OPEN");

        var response = service.assemble(
                "summary",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                data(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                receivable("SETTLED", "100.00", "0.00"),
                                receivable("CANCELLED", "100.00", "0.00"),
                                receivable(" closed ", "100.00", "0.00"),
                                blankStatus,
                                open
                        ),
                        List.of(
                                payable("SETTLED", "200.00", "0.00"),
                                payable("OPEN", null, null)
                        ),
                        List.of(
                                inventoryTransaction("-2.5000", LocalDateTime.of(2026, 7, 1, 8, 0)),
                                inventoryTransaction(null, null)
                        ),
                        List.of(),
                        List.of(
                                operationLog("failure", LocalDateTime.of(2026, 7, 1, 8, 30)),
                                operationLog("SUCCESS", null)
                        ),
                        List.of(exceptionTicket("CLOSED"), exceptionTicket("RESOLVED"), exceptionTicket(null))
                )
        );

        assertThat(response.summary().openReceivableAmount()).isEqualByComparingTo("10.00");
        assertThat(response.summary().openPayableAmount()).isEqualByComparingTo("0.00");
        assertThat(response.summary().inventoryMovementQuantity()).isEqualByComparingTo("2.5000");
        assertThat(response.summary().failedOperationCount()).isEqualTo(1);
        assertThat(response.summary().openExceptionTicketCount()).isEqualTo(2);
        assertThat(response.timeline().get(response.timeline().size() - 1).occurredAt()).isNull();
    }

    @Test
    void emptyResponseUsesConfiguredMoneyAndQuantityScales() {
        LocalDateTime generatedAt = LocalDateTime.of(2026, 7, 2, 11, 30);

        var response = service.empty("", generatedAt);

        assertThat(response.documents()).isEmpty();
        assertThat(response.timeline()).isEmpty();
        assertThat(response.exceptionTickets()).isEmpty();
        assertThat(response.summary().openReceivableAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.summary().openPayableAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.summary().inventoryMovementQuantity()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(response.generatedAt()).isEqualTo(generatedAt);
    }

    private static BusinessTraceAssemblyService.TraceData data(
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
        return new BusinessTraceAssemblyService.TraceData(
                salesOrders,
                purchaseOrders,
                salesDeliveries,
                purchaseReceipts,
                receivables,
                payables,
                inventoryTransactions,
                workflowTasks,
                operationLogs,
                exceptionTickets
        );
    }

    private static SalesOrderEntity salesOrder() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(1001L);
        entity.setOrderNo("SO /001");
        entity.setCustomerId(501L);
        entity.setOrderDate(null);
        entity.setStatus("OPEN");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus("PARTIAL");
        entity.setTotalQuantity(new BigDecimal("10.0000"));
        entity.setTotalAmount(new BigDecimal("1200.00"));
        entity.setCreatedTime(null);
        return entity;
    }

    private static PurchaseOrderEntity purchaseOrder() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(2001L);
        entity.setOrderNo("PO-001");
        entity.setSupplierId(601L);
        entity.setOrderDate(LocalDate.of(2026, 6, 18));
        entity.setStatus("OPEN");
        entity.setApprovalStatus("APPROVED");
        entity.setReceiptStatus("PARTIAL");
        entity.setTotalQuantity(new BigDecimal("8.0000"));
        entity.setTotalAmount(new BigDecimal("800.00"));
        return entity;
    }

    private static SalesDeliveryEntity salesDelivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(3001L);
        entity.setDeliveryNo("SD-001");
        entity.setOrderId(1001L);
        entity.setDeliveryDate(LocalDate.of(2026, 6, 21));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("5.0000"));
        entity.setTotalAmount(new BigDecimal("600.00"));
        return entity;
    }

    private static PurchaseReceiptEntity purchaseReceipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(4001L);
        entity.setReceiptNo("PR-001");
        entity.setOrderId(2001L);
        entity.setReceiptDate(LocalDate.of(2026, 6, 19));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("300.00"));
        return entity;
    }

    private static ReceivableEntity receivable(String status, String originalAmount, String settledAmount) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(5001L);
        entity.setReceivableNo("AR-001");
        entity.setSourceType("SALES_ORDER");
        entity.setSourceNo("SO /001");
        entity.setCustomerId(501L);
        entity.setBizDate(LocalDate.of(2026, 6, 22));
        entity.setOriginalAmount(decimal(originalAmount));
        entity.setSettledAmount(decimal(settledAmount));
        entity.setStatus(status);
        return entity;
    }

    private static PayableEntity payable(String status, String originalAmount, String settledAmount) {
        PayableEntity entity = new PayableEntity();
        entity.setId(6001L);
        entity.setPayableNo("AP-001");
        entity.setSourceType("PURCHASE_ORDER");
        entity.setSourceNo("PO-001");
        entity.setSupplierId(601L);
        entity.setBizDate(LocalDate.of(2026, 6, 23));
        entity.setOriginalAmount(decimal(originalAmount));
        entity.setSettledAmount(decimal(settledAmount));
        entity.setStatus(status);
        return entity;
    }

    private static InventoryTransactionEntity inventoryTransaction(String quantity, LocalDateTime occurredAt) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7001L);
        entity.setProductId(901L);
        entity.setBizNo("SO /001");
        entity.setDirection("OUT");
        entity.setQty(decimal(quantity));
        entity.setOccurredTime(occurredAt);
        return entity;
    }

    private static WorkflowTaskEntity workflowTask() {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(8001L);
        entity.setBusinessNo("SO /001");
        entity.setTitle("SO /001 待审批");
        entity.setStatus("PENDING");
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 20, 10, 0));
        return entity;
    }

    private static OperationLogEntity operationLog(String result, LocalDateTime operationTime) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(9001L);
        entity.setModule("sales");
        entity.setOperation("post");
        entity.setBizNo("SO /001");
        entity.setResult(result);
        entity.setMessage("过账结果");
        entity.setOperationTime(operationTime);
        return entity;
    }

    private static ExceptionTicketEntity exceptionTicket(String status) {
        ExceptionTicketEntity entity = new ExceptionTicketEntity();
        entity.setId(9501L);
        entity.setTicketNo("ET /001");
        entity.setCategory("DELIVERY_DELAY");
        entity.setPriority("HIGH");
        entity.setTitle("销售订单发货异常");
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(1001L);
        entity.setSourceNo("SO /001");
        entity.setStatus(status);
        entity.setAssigneeUserId(9002L);
        entity.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        entity.setUpdatedTime(LocalDateTime.of(2026, 6, 24, 10, 30));
        return entity;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}

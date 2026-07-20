package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.tuowei.erp.report.service.BusinessTraceService;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessTraceServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9001L,
            101L,
            202L,
            301L,
            401L,
            "trace-user",
            "追踪用户"
    );

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private PayableMapper payableMapper;

    @Mock
    private ReceivableMapper receivableMapper;

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @Mock
    private WorkflowTaskMapper workflowTaskMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private ExceptionTicketMapper exceptionTicketMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(ReceivableEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
        initTableInfo(WorkflowTaskEntity.class);
        initTableInfo(OperationLogEntity.class);
        initTableInfo(ExceptionTicketEntity.class);
    }

    @Test
    void aggregatesDocumentsTimelineSummaryAndTenantScopedQueries() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(salesOrder()));
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(purchaseOrder()));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of(salesDelivery()));
        when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of(purchaseReceipt()));
        when(receivableMapper.selectList(any())).thenReturn(List.of(receivable()));
        when(payableMapper.selectList(any())).thenReturn(List.of(payable()));
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(inventoryTransaction()));
        when(workflowTaskMapper.selectList(any())).thenReturn(List.of(workflowTask()));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(failedOperation()));
        when(exceptionTicketMapper.selectList(any())).thenReturn(List.of(exceptionTicket()));

        var response = service().trace(new BusinessTraceQuery(" SO-001 "));

        assertThat(response.keyword()).isEqualTo("SO-001");
        assertThat(response.documents())
                .extracting("documentType")
                .contains("SALES_ORDER", "PURCHASE_ORDER", "SALES_DELIVERY", "PURCHASE_RECEIPT", "RECEIVABLE", "PAYABLE");
        assertThat(response.timeline())
                .extracting("eventType")
                .contains("ORDER", "FULFILLMENT", "FINANCE", "INVENTORY", "WORKFLOW", "OPERATION_LOG");
        assertThat(response.summary().documentCount()).isEqualTo(6);
        assertThat(response.summary().timelineCount()).isEqualTo(9);
        assertThat(response.summary().openReceivableAmount()).isEqualByComparingTo("700.00");
        assertThat(response.summary().openPayableAmount()).isEqualByComparingTo("500.00");
        assertThat(response.summary().inventoryMovementQuantity()).isEqualByComparingTo("5.0000");
        assertThat(response.summary().failedOperationCount()).isEqualTo(1);
        assertThat(response.exceptionTickets())
                .extracting("ticketNo")
                .containsExactly("ET-20260630-0001");
        assertThat(response.summary().openExceptionTicketCount()).isEqualTo(1);
        assertThat(response.generatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 30, 10, 0));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderEntity>> salesCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderMapper).selectList(salesCaptor.capture());
        assertTenantScoped(salesCaptor.getValue().getSqlSegment());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> inventoryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper).selectList(inventoryCaptor.capture());
        assertTenantScoped(inventoryCaptor.getValue().getSqlSegment());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEntity>> ticketCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(exceptionTicketMapper).selectList(ticketCaptor.capture());
        assertTenantScoped(ticketCaptor.getValue().getSqlSegment());
    }

    @Test
    void blankKeywordReturnsEmptyTraceWithoutMapperQueries() {
        var response = service().trace(new BusinessTraceQuery("  "));

        assertThat(response.keyword()).isEmpty();
        assertThat(response.documents()).isEmpty();
        assertThat(response.timeline()).isEmpty();
        assertThat(response.summary().documentCount()).isZero();
        assertThat(response.generatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 30, 10, 0));
        verifyNoInteractions(
                currentUserContext,
                purchaseOrderMapper,
                salesOrderMapper,
                purchaseReceiptMapper,
                salesDeliveryMapper,
                payableMapper,
                receivableMapper,
                inventoryTransactionMapper,
                workflowTaskMapper,
                operationLogMapper,
                exceptionTicketMapper
        );
    }

    private BusinessTraceService service() {
        return new BusinessTraceService(
                currentUserContext,
                purchaseOrderMapper,
                salesOrderMapper,
                purchaseReceiptMapper,
                salesDeliveryMapper,
                payableMapper,
                receivableMapper,
                inventoryTransactionMapper,
                workflowTaskMapper,
                operationLogMapper,
                exceptionTicketMapper,
                Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    private static void assertTenantScoped(String sqlSegment) {
        assertThat(sqlSegment.toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private static SalesOrderEntity salesOrder() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(1001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setOrderNo("SO-001");
        entity.setCustomerId(501L);
        entity.setOrderDate(LocalDate.of(2026, 6, 20));
        entity.setStatus("OPEN");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus("PARTIAL");
        entity.setTotalQuantity(new BigDecimal("10.0000"));
        entity.setTotalAmount(new BigDecimal("1200.00"));
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 20, 9, 0));
        return entity;
    }

    private static PurchaseOrderEntity purchaseOrder() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(2001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setOrderNo("PO-001");
        entity.setSupplierId(601L);
        entity.setOrderDate(LocalDate.of(2026, 6, 18));
        entity.setStatus("OPEN");
        entity.setApprovalStatus("APPROVED");
        entity.setReceiptStatus("PARTIAL");
        entity.setTotalQuantity(new BigDecimal("8.0000"));
        entity.setTotalAmount(new BigDecimal("800.00"));
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 18, 10, 0));
        return entity;
    }

    private static SalesDeliveryEntity salesDelivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(3001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setDeliveryNo("SD-001");
        entity.setOrderId(1001L);
        entity.setDeliveryDate(LocalDate.of(2026, 6, 21));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("5.0000"));
        entity.setTotalAmount(new BigDecimal("600.00"));
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 21, 11, 0));
        return entity;
    }

    private static PurchaseReceiptEntity purchaseReceipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(4001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setReceiptNo("PR-001");
        entity.setOrderId(2001L);
        entity.setReceiptDate(LocalDate.of(2026, 6, 19));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("300.00"));
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 19, 11, 0));
        return entity;
    }

    private static ReceivableEntity receivable() {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(5001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setReceivableNo("AR-001");
        entity.setSourceType("SALES_ORDER");
        entity.setSourceNo("SO-001");
        entity.setCustomerId(501L);
        entity.setBizDate(LocalDate.of(2026, 6, 22));
        entity.setOriginalAmount(new BigDecimal("1000.00"));
        entity.setSettledAmount(new BigDecimal("300.00"));
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 22, 9, 0));
        return entity;
    }

    private static PayableEntity payable() {
        PayableEntity entity = new PayableEntity();
        entity.setId(6001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setPayableNo("AP-001");
        entity.setSourceType("PURCHASE_ORDER");
        entity.setSourceNo("PO-001");
        entity.setSupplierId(601L);
        entity.setBizDate(LocalDate.of(2026, 6, 23));
        entity.setOriginalAmount(new BigDecimal("700.00"));
        entity.setSettledAmount(new BigDecimal("200.00"));
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 23, 9, 0));
        return entity;
    }

    private static InventoryTransactionEntity inventoryTransaction() {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(801L);
        entity.setProductId(901L);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD-001");
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal("5.0000"));
        entity.setAmount(new BigDecimal("600.00"));
        entity.setOccurredTime(LocalDateTime.of(2026, 6, 21, 12, 0));
        return entity;
    }

    private static WorkflowTaskEntity workflowTask() {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(8001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setBusinessType("SALES_ORDER");
        entity.setBusinessId(1001L);
        entity.setBusinessNo("SO-001");
        entity.setTitle("SO-001 待审批");
        entity.setApproverUserId(USER.userId());
        entity.setStatus("PENDING");
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 20, 10, 0));
        return entity;
    }

    private static OperationLogEntity failedOperation() {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(9001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setModule("sales");
        entity.setOperation("post");
        entity.setBizNo("SO-001");
        entity.setResult("FAILURE");
        entity.setMessage("过账失败");
        entity.setRequestUri("/api/sales/orders/1001/post");
        entity.setOperationTime(LocalDateTime.of(2026, 6, 24, 9, 0));
        return entity;
    }

    private static ExceptionTicketEntity exceptionTicket() {
        ExceptionTicketEntity entity = new ExceptionTicketEntity();
        entity.setId(9501L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setTicketNo("ET-20260630-0001");
        entity.setCategory("DELIVERY_DELAY");
        entity.setPriority("HIGH");
        entity.setTitle("销售订单发货异常");
        entity.setDescription("SO-001 部分商品未发出");
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(1001L);
        entity.setSourceNo("SO-001");
        entity.setSourceRoute("/sales/orders?keyword=SO-001");
        entity.setStatus("OPEN");
        entity.setAssigneeUserId(9002L);
        entity.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(USER.userId());
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 24, 10, 0));
        entity.setUpdatedTime(LocalDateTime.of(2026, 6, 24, 10, 30));
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import com.tuowei.erp.sales.order.service.SalesOrderPostingService;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderPostingServiceTest {

    private static final Long ORDER_ID = 1001L;
    private static final Long COMPANY_ID = 100L;
    private static final Long ACCOUNT_BOOK_ID = 200L;
    private static final Long USER_ID = 300L;
    private static final Long CUSTOMER_ID = 400L;
    private static final Long WAREHOUSE_ID = 500L;
    private static final Long LINE_ID = 600L;
    private static final Long PRODUCT_ID = 700L;

    @Mock
    private SalesOrderMapper salesOrderMapper;
    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private InventoryPostingService inventoryPostingService;
    @Mock
    private SalesOrderQueryService salesOrderQueryService;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private SalesCreditEvaluator salesCreditEvaluator;

    private SalesOrderPostingService postingService;
    private AuditMetadata audit;

    @BeforeEach
    void setUp() {
        postingService = new SalesOrderPostingService(
                salesOrderMapper,
                salesOrderLineMapper,
                customerMapper,
                inventoryPostingService,
                salesOrderQueryService,
                auditMetadataFactory,
                workflowService,
                salesCreditEvaluator
        );

        audit = new AuditMetadata(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, LocalDateTime.now());
        lenient().when(auditMetadataFactory.current()).thenReturn(audit);
    }

    @Test
    void approve_shouldReserveInventoryAndUpdateStatus() {
        // Given
        SalesOrderEntity order = buildSubmittedOrder();
        CustomerEntity customer = buildActiveCustomer();
        SalesOrderLineEntity line = buildOrderLine();
        SalesOrderResponse expectedResponse = buildResponse();

        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        when(salesOrderLineMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(line));
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(expectedResponse);

        // When
        SalesOrderResponse result = postingService.approve(ORDER_ID, new SalesOrderApproveRequest("审批通过"));

        // Then
        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<SalesOrderEntity> entityCaptor = ArgumentCaptor.forClass(SalesOrderEntity.class);
        verify(salesOrderMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(entityCaptor.getValue().getApprovalStatus()).isEqualTo("APPROVED");

        ArgumentCaptor<InventoryReservationCommand> cmdCaptor = ArgumentCaptor.forClass(InventoryReservationCommand.class);
        verify(inventoryPostingService).reserve(cmdCaptor.capture(), eq(audit), anyString());
        assertThat(cmdCaptor.getValue().warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(cmdCaptor.getValue().productId()).isEqualTo(PRODUCT_ID);
        assertThat(cmdCaptor.getValue().sourceType()).isEqualTo("SALES_ORDER");
        assertThat(cmdCaptor.getValue().sourceId()).isEqualTo(ORDER_ID);

        verify(workflowService).approve("SALES_ORDER", ORDER_ID, "审批通过");
        verify(salesCreditEvaluator).assertWithinCreditLimit(customer, order, "审批");
    }

    @Test
    void approve_shouldThrowWhenStatusNotSubmitted() {
        // Given
        SalesOrderEntity order = buildSubmittedOrder();
        order.setStatus("DRAFT");
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        // When & Then
        assertThatThrownBy(() -> postingService.approve(ORDER_ID, new SalesOrderApproveRequest("审批")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许审批通过");
    }

    @Test
    void approveWorkflowTask_shouldCallWorkflowServiceWithTaskId() {
        // Given
        Long taskId = 9001L;
        SalesOrderEntity order = buildSubmittedOrder();
        CustomerEntity customer = buildActiveCustomer();
        SalesOrderLineEntity line = buildOrderLine();

        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        when(salesOrderLineMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(line));
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(buildResponse());

        // When
        postingService.approveWorkflowTask(taskId, ORDER_ID, new SalesOrderApproveRequest("通过"));

        // Then
        verify(workflowService).approveTaskForBusiness(taskId, "SALES_ORDER", ORDER_ID, "通过");
    }

    @Test
    void reject_shouldUpdateStatusAndCallWorkflow() {
        // Given
        SalesOrderEntity order = buildSubmittedOrder();
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(buildResponse());

        // When
        postingService.reject(ORDER_ID, new SalesOrderRejectRequest("不符合要求"));

        // Then
        ArgumentCaptor<SalesOrderEntity> entityCaptor = ArgumentCaptor.forClass(SalesOrderEntity.class);
        verify(salesOrderMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(entityCaptor.getValue().getApprovalStatus()).isEqualTo("REJECTED");

        verify(workflowService).reject("SALES_ORDER", ORDER_ID, "不符合要求");
    }

    @Test
    void unapprove_shouldReleaseReservationsAndResetStatus() {
        // Given
        SalesOrderEntity order = buildApprovedOrder();
        order.setDeliveryStatus("NOT_DELIVERED");
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(buildResponse());

        // When
        postingService.unapprove(ORDER_ID);

        // Then
        verify(inventoryPostingService).releaseAllReservations("SALES_ORDER", ORDER_ID, audit);

        ArgumentCaptor<SalesOrderEntity> entityCaptor = ArgumentCaptor.forClass(SalesOrderEntity.class);
        verify(salesOrderMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(entityCaptor.getValue().getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
    }

    @Test
    void unapprove_shouldThrowWhenAlreadyDelivered() {
        // Given
        SalesOrderEntity order = buildApprovedOrder();
        order.setDeliveryStatus("PARTIAL_DELIVERED");
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        // When & Then
        assertThatThrownBy(() -> postingService.unapprove(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已出库销售订单不允许反审核");
    }

    @Test
    void cancel_shouldReleaseReservationsForApprovedOrder() {
        // Given
        SalesOrderEntity order = buildApprovedOrder();
        order.setDeliveryStatus("NOT_DELIVERED");
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(buildResponse());

        // When
        postingService.cancel(ORDER_ID);

        // Then
        verify(inventoryPostingService).releaseAllReservations("SALES_ORDER", ORDER_ID, audit);
        verify(workflowService).cancel("SALES_ORDER", ORDER_ID, "作废销售订单");

        ArgumentCaptor<SalesOrderEntity> entityCaptor = ArgumentCaptor.forClass(SalesOrderEntity.class);
        verify(salesOrderMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_shouldNotReleaseReservationsForDraftOrder() {
        // Given
        SalesOrderEntity order = buildSubmittedOrder();
        order.setStatus("DRAFT");
        order.setApprovalStatus("NOT_SUBMITTED");
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(buildResponse());

        // When
        postingService.cancel(ORDER_ID);

        // Then
        verify(inventoryPostingService, never()).releaseAllReservations(anyString(), anyLong(), any());
        verify(workflowService).cancel("SALES_ORDER", ORDER_ID, "作废销售订单");
    }

    private SalesOrderEntity buildSubmittedOrder() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setOrderNo("SO20260804001");
        entity.setOrderDate(LocalDate.now());
        entity.setStatus("SUBMITTED");
        entity.setApprovalStatus("IN_APPROVAL");
        entity.setDeliveryStatus("NOT_DELIVERED");
        entity.setTotalAmount(new BigDecimal("1000.00"));
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private SalesOrderEntity buildApprovedOrder() {
        SalesOrderEntity entity = buildSubmittedOrder();
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        return entity;
    }

    private CustomerEntity buildActiveCustomer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCompanyId(COMPANY_ID);
        customer.setAccountBookId(ACCOUNT_BOOK_ID);
        customer.setCustomerName("测试客户");
        customer.setStatus("ACTIVE");
        customer.setDeletedFlag(0);
        return customer;
    }

    private SalesOrderLineEntity buildOrderLine() {
        SalesOrderLineEntity line = new SalesOrderLineEntity();
        line.setId(LINE_ID);
        line.setCompanyId(COMPANY_ID);
        line.setAccountBookId(ACCOUNT_BOOK_ID);
        line.setOrderId(ORDER_ID);
        line.setLineNo(1);
        line.setProductId(PRODUCT_ID);
        line.setQty(new BigDecimal("10.00"));
        line.setPrice(new BigDecimal("100.00"));
        line.setAmount(new BigDecimal("1000.00"));
        return line;
    }

    private SalesOrderResponse buildResponse() {
        return new SalesOrderResponse(
                ORDER_ID,
                "SO20260804001",
                CUSTOMER_ID,
                WAREHOUSE_ID,
                "测试客户",
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                "APPROVED",
                "APPROVED",
                "NOT_DELIVERED",
                new BigDecimal("10.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("130.00"),
                null,
                List.of()
        );
    }
}

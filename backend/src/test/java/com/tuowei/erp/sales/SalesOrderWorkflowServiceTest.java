package com.tuowei.erp.sales;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.service.SalesOrderWorkflowService;
import com.tuowei.erp.sales.order.service.SalesPriceEvaluator;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderWorkflowServiceTest {

    private static final Long ORDER_ID = 4301L;
    private static final Long CUSTOMER_ID = 4101L;
    private static final Long WAREHOUSE_ID = 4201L;
    private static final Long WORKFLOW_TASK_ID = 9901L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            9932L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 30)
    );

    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private InventoryPostingService inventoryPostingService;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SalesOrderQueryService salesOrderQueryService;
    @Mock private WorkflowService workflowService;
    @Mock private SalesCreditEvaluator salesCreditEvaluator;
    @Mock private SalesPriceEvaluator salesPriceEvaluator;
    @Mock private AttachmentService attachmentService;

    @Test
    void submitRunsAttachmentPriceCreditTransitionAndWorkflowInOrder() {
        SalesOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        SalesOrderLineEntity line = line(4302L, "2.0000", "15.0000", "0.1300");
        SalesOrderResponse expected = response("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(salesOrderQueryService.selectLines(draft)).thenReturn(List.of(line));
        CustomerEntity customer = customer();
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        stubTransition(draft, expected);

        assertThat(service().submit(ORDER_ID, new SalesOrderSubmitRequest("submit"))).isSameAs(expected);
        assertThat(draft.getStatus()).isEqualTo("SUBMITTED");
        assertThat(draft.getApprovalStatus()).isEqualTo("IN_APPROVAL");

        InOrder order = inOrder(
                salesOrderQueryService, attachmentService, salesPriceEvaluator,
                customerMapper, salesCreditEvaluator, auditMetadataFactory,
                salesOrderMapper, workflowService
        );
        order.verify(salesOrderQueryService).requireOrder(ORDER_ID);
        order.verify(attachmentService).requireIfConfigured(AttachmentBusinessType.SALES_ORDER, ORDER_ID);
        order.verify(salesOrderQueryService).selectLines(draft);
        ArgumentCaptor<List<SalesOrderLineRequest>> lineRequests = ArgumentCaptor.forClass(List.class);
        order.verify(salesPriceEvaluator).assertLinesWithinMinPrice(
                eq(AUDIT.companyId()), eq(AUDIT.accountBookId()), eq(CUSTOMER_ID),
                eq(draft.getOrderDate()), lineRequests.capture()
        );
        assertThat(lineRequests.getValue()).containsExactly(new SalesOrderLineRequest(
                4302L,
                new BigDecimal("2.0000"),
                new BigDecimal("15.0000"),
                new BigDecimal("0.1300"),
                "line remark"
        ));
        order.verify(customerMapper).selectById(CUSTOMER_ID);
        order.verify(salesCreditEvaluator).assertWithinCreditLimit(same(customer), same(draft), eq("提交"));
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderMapper).updateById(same(draft));
        order.verify(salesOrderQueryService).getById(ORDER_ID);
        order.verify(workflowService).submit("SALES_ORDER", ORDER_ID, "SO-4301", "销售订单 SO-4301", "submit");
    }

    @Test
    void submitAllowsRejectedOrderAndMovesItBackIntoApproval() {
        SalesOrderEntity rejected = order("REJECTED", "REJECTED", "NOT_DELIVERED");
        SalesOrderResponse expected = response("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(rejected);
        when(salesOrderQueryService.selectLines(rejected)).thenReturn(List.of());
        stubTransition(rejected, expected);

        assertThat(service().submit(ORDER_ID, new SalesOrderSubmitRequest("retry"))).isSameAs(expected);

        assertThat(rejected.getStatus()).isEqualTo("SUBMITTED");
        assertThat(rejected.getApprovalStatus()).isEqualTo("IN_APPROVAL");
        verify(workflowService).submit("SALES_ORDER", ORDER_ID, "SO-4301", "销售订单 SO-4301", "retry");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "APPROVED", "CANCELLED", "CLOSED"})
    void submitRejectsIllegalStatusBeforeGates(String status) {
        SalesOrderEntity order = order(status, "IN_APPROVAL", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许提交审批");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(attachmentService, salesPriceEvaluator, customerMapper, salesCreditEvaluator);
    }

    @Test
    void submitAttachmentFailureStopsAllDownstreamWork() {
        SalesOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        doThrow(new IllegalArgumentException("销售订单必须上传附件"))
                .when(attachmentService).requireIfConfigured(AttachmentBusinessType.SALES_ORDER, ORDER_ID);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("gate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单必须上传附件");
        verify(salesOrderQueryService, never()).selectLines(any());
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void submitPriceFailureStopsBeforeCreditAndTransition() {
        SalesOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        SalesOrderLineEntity line = line(4302L, "1.0000", "5.0000", "0.0000");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(salesOrderQueryService.selectLines(draft)).thenReturn(List.of(line));
        doThrow(new IllegalArgumentException("第 1 行单价低于生效最低价"))
                .when(salesPriceEvaluator)
                .assertLinesWithinMinPrice(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("price")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("第 1 行单价低于生效最低价");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verify(customerMapper, never()).selectById(any());
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void submitCreditFailureStopsBeforeTransition() {
        SalesOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        CustomerEntity customer = customer();
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(salesOrderQueryService.selectLines(draft)).thenReturn(List.of());
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        doThrow(new IllegalArgumentException("客户信用额度不足"))
                .when(salesCreditEvaluator)
                .assertWithinCreditLimit(same(customer), same(draft), eq("提交"));

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("credit")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户信用额度不足");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void directApproveReservesLinesThenTransitionsBeforeWorkflow() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        SalesOrderLineEntity firstLine = line(4302L, "2.0000", "15.0000", "0.0000");
        SalesOrderLineEntity secondLine = line(4303L, "1.5000", "12.0000", "0.1300");
        secondLine.setLineNo(2);
        SalesOrderResponse expected = response("APPROVED", "APPROVED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        when(salesOrderQueryService.selectLines(submitted)).thenReturn(List.of(firstLine, secondLine));
        CustomerEntity customer = customer();
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        stubTransition(submitted, expected);

        assertThat(service().approve(ORDER_ID, new SalesOrderApproveRequest("approve"))).isSameAs(expected);

        ArgumentCaptor<InventoryReservationCommand> command = ArgumentCaptor.forClass(InventoryReservationCommand.class);
        InOrder order = inOrder(
                salesOrderQueryService, customerMapper, salesCreditEvaluator,
                auditMetadataFactory, inventoryPostingService, salesOrderMapper, workflowService
        );
        order.verify(salesOrderQueryService).requireOrder(ORDER_ID);
        order.verify(customerMapper).selectById(CUSTOMER_ID);
        order.verify(salesCreditEvaluator).assertWithinCreditLimit(same(customer), same(submitted), eq("审批"));
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderQueryService).selectLines(submitted);
        order.verify(inventoryPostingService, org.mockito.Mockito.times(2)).reserve(
                command.capture(), same(AUDIT), eq("库存可用量不足，不能审批销售订单")
        );
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderMapper).updateById(same(submitted));
        order.verify(salesOrderQueryService).getById(ORDER_ID);
        order.verify(workflowService).approve("SALES_ORDER", ORDER_ID, "approve");
        assertThat(command.getAllValues()).hasSize(2);
        assertThat(command.getAllValues().get(0)).extracting(
                InventoryReservationCommand::warehouseId,
                InventoryReservationCommand::productId,
                InventoryReservationCommand::sourceType,
                InventoryReservationCommand::sourceId,
                InventoryReservationCommand::sourceNo,
                InventoryReservationCommand::sourceLineId,
                InventoryReservationCommand::qty
        ).containsExactly(WAREHOUSE_ID, 4302L, "SALES_ORDER", ORDER_ID, "SO-4301", 4302L, new BigDecimal("2.0000"));
        assertThat(command.getAllValues().get(0).remark()).isEqualTo("line remark");
        assertThat(command.getAllValues().get(1)).extracting(
                InventoryReservationCommand::warehouseId,
                InventoryReservationCommand::productId,
                InventoryReservationCommand::sourceType,
                InventoryReservationCommand::sourceId,
                InventoryReservationCommand::sourceNo,
                InventoryReservationCommand::sourceLineId,
                InventoryReservationCommand::qty
        ).containsExactly(WAREHOUSE_ID, 4303L, "SALES_ORDER", ORDER_ID, "SO-4301", 4303L, new BigDecimal("1.5000"));
    }

    @Test
    void taskApproveUsesMatchingWorkflowTask() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        SalesOrderResponse expected = response("APPROVED", "APPROVED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        when(salesOrderQueryService.selectLines(submitted)).thenReturn(List.of());
        stubTransition(submitted, expected);

        assertThat(service().approveWorkflowTask(WORKFLOW_TASK_ID, ORDER_ID, new SalesOrderApproveRequest("task")))
                .isSameAs(expected);

        assertThat(submitted.getStatus()).isEqualTo("APPROVED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("APPROVED");
        verify(salesOrderMapper).updateById(same(submitted));
        verify(workflowService).approveTaskForBusiness(WORKFLOW_TASK_ID, "SALES_ORDER", ORDER_ID, "task");
        verify(workflowService, never()).approve(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "REJECTED, REJECTED",
            "APPROVED, APPROVED",
            "CANCELLED, CANCELLED",
            "SUBMITTED, NOT_SUBMITTED",
            "SUBMITTED, APPROVED",
            "SUBMITTED, PENDING"
    })
    void approveRejectsInvalidLifecycleCombinations(String status, String approvalStatus) {
        SalesOrderEntity order = order(status, approvalStatus, "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().approve(ORDER_ID, new SalesOrderApproveRequest("bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许审批通过");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(customerMapper, salesCreditEvaluator, inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void approveCreditFailureStopsBeforeReservationAndTransition() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        CustomerEntity customer = customer();
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        doThrow(new IllegalArgumentException("客户信用额度不足"))
                .when(salesCreditEvaluator)
                .assertWithinCreditLimit(same(customer), same(submitted), eq("审批"));

        assertThatThrownBy(() -> service().approve(ORDER_ID, new SalesOrderApproveRequest("credit")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户信用额度不足");

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void approveReservationFailureStopsBeforeTransition() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        SalesOrderLineEntity line = line(4302L, "2.0000", "15.0000", "0.0000");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        when(salesOrderQueryService.selectLines(submitted)).thenReturn(List.of(line));
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        doThrow(new IllegalArgumentException("库存可用量不足，不能审批销售订单"))
                .when(inventoryPostingService)
                .reserve(any(), same(AUDIT), eq("库存可用量不足，不能审批销售订单"));

        assertThatThrownBy(() -> service().approve(ORDER_ID, new SalesOrderApproveRequest("stock")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存可用量不足，不能审批销售订单");

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(salesOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void rejectTransitionsBeforeDirectWorkflow() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        SalesOrderResponse expected = response("REJECTED", "REJECTED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubTransition(submitted, expected);

        assertThat(service().reject(ORDER_ID, new SalesOrderRejectRequest("reason"))).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("REJECTED");
        InOrder order = inOrder(salesOrderQueryService, auditMetadataFactory, salesOrderMapper, workflowService);
        order.verify(salesOrderQueryService).requireOrder(ORDER_ID);
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderMapper).updateById(same(submitted));
        order.verify(salesOrderQueryService).getById(ORDER_ID);
        order.verify(workflowService).reject("SALES_ORDER", ORDER_ID, "reason");
    }

    @Test
    void taskRejectUsesMatchingWorkflowTask() {
        SalesOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_DELIVERED");
        SalesOrderResponse expected = response("REJECTED", "REJECTED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubTransition(submitted, expected);

        assertThat(service().rejectWorkflowTask(WORKFLOW_TASK_ID, ORDER_ID, new SalesOrderRejectRequest("task reason")))
                .isSameAs(expected);

        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("REJECTED");
        verify(salesOrderMapper).updateById(same(submitted));
        verify(workflowService).rejectTaskForBusiness(WORKFLOW_TASK_ID, "SALES_ORDER", ORDER_ID, "task reason");
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "REJECTED, REJECTED",
            "APPROVED, APPROVED",
            "CANCELLED, CANCELLED",
            "SUBMITTED, NOT_SUBMITTED",
            "SUBMITTED, APPROVED",
            "SUBMITTED, PENDING"
    })
    void rejectRejectsInvalidLifecycleCombinations(String status, String approvalStatus) {
        SalesOrderEntity order = order(status, approvalStatus, "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().reject(ORDER_ID, new SalesOrderRejectRequest("invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许驳回");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(customerMapper, salesCreditEvaluator, inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void unapproveReleasesReservationsBeforeReturningToDraft() {
        SalesOrderEntity approved = order("APPROVED", "APPROVED", "NOT_DELIVERED");
        SalesOrderResponse expected = response("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        stubTransition(approved, expected);

        assertThat(service().unapprove(ORDER_ID)).isSameAs(expected);
        assertThat(approved.getStatus()).isEqualTo("DRAFT");
        assertThat(approved.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        InOrder order = inOrder(inventoryPostingService, auditMetadataFactory, salesOrderMapper, salesOrderQueryService);
        order.verify(auditMetadataFactory).current();
        order.verify(inventoryPostingService).releaseAllReservations(eq("SALES_ORDER"), eq(ORDER_ID), same(AUDIT));
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderMapper).updateById(same(approved));
        order.verify(salesOrderQueryService).getById(ORDER_ID);
        verifyNoInteractions(workflowService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL_DELIVERED", "FULL_DELIVERED"})
    void unapproveRejectsDeliveredOrderBeforeRelease(String deliveryStatus) {
        SalesOrderEntity delivered = order("APPROVED", "APPROVED", deliveryStatus);
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(delivered);

        assertThatThrownBy(() -> service().unapprove(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已出库销售订单不允许反审核");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(inventoryPostingService, auditMetadataFactory);
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "REJECTED, REJECTED",
            "SUBMITTED, IN_APPROVAL",
            "APPROVED, IN_APPROVAL",
            "APPROVED, NOT_SUBMITTED"
    })
    void unapproveRejectsInvalidLifecycleCombinations(String status, String approvalStatus) {
        SalesOrderEntity order = order(status, approvalStatus, "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().unapprove(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许反审核");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void unapproveReleaseFailureStopsBeforeTransition() {
        SalesOrderEntity approved = order("APPROVED", "APPROVED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        doThrow(new IllegalArgumentException("释放预占失败"))
                .when(inventoryPostingService)
                .releaseAllReservations("SALES_ORDER", ORDER_ID, AUDIT);

        assertThatThrownBy(() -> service().unapprove(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("释放预占失败");
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(salesOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void approvedCancelReleasesReservationsThenCancelsAndClosesWorkflow() {
        SalesOrderEntity approved = order("APPROVED", "APPROVED", "NOT_DELIVERED");
        SalesOrderResponse expected = response("CANCELLED", "CANCELLED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        stubTransition(approved, expected);

        assertThat(service().cancel(ORDER_ID)).isSameAs(expected);
        assertThat(approved.getStatus()).isEqualTo("CANCELLED");
        assertThat(approved.getApprovalStatus()).isEqualTo("CANCELLED");
        InOrder order = inOrder(inventoryPostingService, auditMetadataFactory, salesOrderMapper, salesOrderQueryService, workflowService);
        order.verify(auditMetadataFactory).current();
        order.verify(inventoryPostingService).releaseAllReservations(eq("SALES_ORDER"), eq(ORDER_ID), same(AUDIT));
        order.verify(auditMetadataFactory).current();
        order.verify(salesOrderMapper).updateById(same(approved));
        order.verify(salesOrderQueryService).getById(ORDER_ID);
        order.verify(workflowService).cancel("SALES_ORDER", ORDER_ID, "作废销售订单");
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "REJECTED, REJECTED",
            "SUBMITTED, IN_APPROVAL"
    })
    void cancelAllowsNonApprovedLifecycleStatesWithoutReleasingReservations(
            String status,
            String approvalStatus
    ) {
        SalesOrderEntity order = order(status, approvalStatus, "NOT_DELIVERED");
        SalesOrderResponse expected = response("CANCELLED", "CANCELLED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);
        stubTransition(order, expected);

        assertThat(service().cancel(ORDER_ID)).isSameAs(expected);
        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getApprovalStatus()).isEqualTo("CANCELLED");
        verify(inventoryPostingService, never()).releaseAllReservations(any(), any(), any());
        verify(workflowService).cancel("SALES_ORDER", ORDER_ID, "作废销售订单");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "CLOSED"})
    void cancelRejectsTerminalStates(String status) {
        SalesOrderEntity order = order(status, status, "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许作废");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void cancelRejectsDeliveredApprovedOrderBeforeRelease() {
        SalesOrderEntity delivered = order("APPROVED", "APPROVED", "FULL_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(delivered);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已出库销售订单不允许作废");
        verifyNoTransitionOrWorkflow();
        verifyNoInteractions(inventoryPostingService, auditMetadataFactory);
    }

    @Test
    void approvedCancelReleaseFailureStopsBeforeTransition() {
        SalesOrderEntity approved = order("APPROVED", "APPROVED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        doThrow(new IllegalArgumentException("释放预占失败"))
                .when(inventoryPostingService)
                .releaseAllReservations("SALES_ORDER", ORDER_ID, AUDIT);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("释放预占失败");
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(salesOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void workflowStopsWhenScopedOrderLookupIsDenied() {
        when(salesOrderQueryService.requireOrder(ORDER_ID))
                .thenThrow(new AccessDeniedException("无权访问销售订单"));

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("denied")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问销售订单");
        verifyNoInteractions(
                attachmentService,
                salesPriceEvaluator,
                customerMapper,
                salesCreditEvaluator,
                inventoryPostingService,
                auditMetadataFactory,
                workflowService
        );
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void optimisticLockConflictStopsDetailAndWorkflow() {
        SalesOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED");
        when(salesOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(salesOrderQueryService.selectLines(draft)).thenReturn(List.of());
        CustomerEntity customer = customer();
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesOrderMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new SalesOrderSubmitRequest("conflict")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("销售订单已被其他操作修改，请刷新后重试");
        verify(salesOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    private SalesOrderWorkflowService service() {
        return new SalesOrderWorkflowService(
                salesOrderMapper, customerMapper, inventoryPostingService,
                auditMetadataFactory, salesOrderQueryService, workflowService,
                salesCreditEvaluator, salesPriceEvaluator, attachmentService
        );
    }

    private void stubTransition(SalesOrderEntity entity, SalesOrderResponse response) {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(salesOrderMapper.updateById(entity)).thenReturn(1);
        when(salesOrderQueryService.getById(ORDER_ID)).thenReturn(response);
    }

    private void verifyNoTransitionOrWorkflow() {
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(salesOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    private SalesOrderEntity order(String status, String approvalStatus, String deliveryStatus) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOrderNo("SO-4301");
        entity.setCustomerId(CUSTOMER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setOrderDate(LocalDate.of(2026, 6, 8));
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        entity.setDeliveryStatus(deliveryStatus);
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderLineEntity line(Long productId, String qty, String price, String taxRate) {
        SalesOrderLineEntity line = new SalesOrderLineEntity();
        line.setId(productId);
        line.setLineNo(1);
        line.setProductId(productId);
        line.setQty(new BigDecimal(qty));
        line.setPrice(new BigDecimal(price));
        line.setTaxRate(new BigDecimal(taxRate));
        line.setRemark("line remark");
        return line;
    }

    private CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCustomerName("customer");
        return customer;
    }

    private SalesOrderResponse response(String status, String approvalStatus, String deliveryStatus) {
        return new SalesOrderResponse(
                ORDER_ID, "SO-4301", CUSTOMER_ID, WAREHOUSE_ID, "customer",
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 9), status,
                approvalStatus, deliveryStatus, new BigDecimal("2.0000"), BigDecimal.TEN,
                BigDecimal.ZERO, "remark", List.of()
        );
    }
}

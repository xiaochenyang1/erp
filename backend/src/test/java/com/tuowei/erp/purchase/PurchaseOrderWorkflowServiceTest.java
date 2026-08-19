package com.tuowei.erp.purchase;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderWorkflowService;
import com.tuowei.erp.purchase.order.service.PurchasePriceEvaluator;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

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
class PurchaseOrderWorkflowServiceTest {

    private static final Long ORDER_ID = 4301L;
    private static final Long SUPPLIER_ID = 4101L;
    private static final Long WORKFLOW_TASK_ID = 9901L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 6, 8);
    private static final AuditMetadata AUDIT = new AuditMetadata(
            9932L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 30)
    );

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private PurchaseOrderQueryService purchaseOrderQueryService;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private PurchasePriceEvaluator purchasePriceEvaluator;

    @Mock
    private AttachmentService attachmentService;

    @Captor
    private ArgumentCaptor<List<PurchaseOrderLineRequest>> lineRequestsCaptor;

    @Test
    void submitChecksGatesMapsLinesUpdatesAuditReturnsDetailThenStartsWorkflow() {
        PurchaseOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_RECEIVED");
        PurchaseOrderLineEntity first = line(4201L, "2.5000", "12.3400", "13.0000", "first");
        PurchaseOrderLineEntity second = line(4202L, "3.7500", "8.9000", "0.0000", "second");
        PurchaseOrderResponse expected = response("SUBMITTED", "IN_APPROVAL", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(purchaseOrderQueryService.selectLines(draft)).thenReturn(List.of(first, second));
        stubSuccessfulTransition(draft, expected);

        PurchaseOrderResponse actual = service().submit(
                ORDER_ID,
                new PurchaseOrderSubmitRequest("submit for approval")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(draft.getStatus()).isEqualTo("SUBMITTED");
        assertThat(draft.getApprovalStatus()).isEqualTo("IN_APPROVAL");
        assertAuditFields(draft);

        InOrder submitOrder = inOrder(
                purchaseOrderQueryService,
                attachmentService,
                purchasePriceEvaluator,
                auditMetadataFactory,
                purchaseOrderMapper,
                workflowService
        );
        submitOrder.verify(purchaseOrderQueryService).requireOrder(ORDER_ID);
        submitOrder.verify(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.PURCHASE_ORDER, ORDER_ID);
        submitOrder.verify(purchaseOrderQueryService).selectLines(draft);
        submitOrder.verify(purchasePriceEvaluator).assertLinesWithinMaxPrice(
                eq(AUDIT.companyId()),
                eq(AUDIT.accountBookId()),
                eq(SUPPLIER_ID),
                eq(ORDER_DATE),
                lineRequestsCaptor.capture()
        );
        submitOrder.verify(auditMetadataFactory).current();
        submitOrder.verify(purchaseOrderMapper).updateById(same(draft));
        submitOrder.verify(purchaseOrderQueryService).getById(ORDER_ID);
        submitOrder.verify(workflowService).submit(
                "PURCHASE_ORDER",
                ORDER_ID,
                "PO-4301",
                "采购订单 PO-4301",
                "submit for approval"
        );

        assertThat(lineRequestsCaptor.getValue()).containsExactly(
                new PurchaseOrderLineRequest(
                        4201L,
                        new BigDecimal("2.5000"),
                        new BigDecimal("12.3400"),
                        new BigDecimal("13.0000"),
                        "first"
                ),
                new PurchaseOrderLineRequest(
                        4202L,
                        new BigDecimal("3.7500"),
                        new BigDecimal("8.9000"),
                        new BigDecimal("0.0000"),
                        "second"
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "APPROVED", "CANCELLED", "CLOSED"})
    void submitRejectsIllegalStatusBeforeAnyGateOrMutation(String status) {
        PurchaseOrderEntity order = order(status, "IN_APPROVAL", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new PurchaseOrderSubmitRequest("invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前采购订单状态不允许提交审批");

        assertThat(order.getStatus()).isEqualTo(status);
        verify(attachmentService, never()).requireIfConfigured(any(), any());
        verify(purchaseOrderQueryService, never()).selectLines(any());
        verifyNoInteractions(purchasePriceEvaluator);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void submitStopsAtAttachmentGateBeforeLoadingLinesOrEvaluatingPrice() {
        PurchaseOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        doThrow(new IllegalArgumentException("采购订单必须上传附件"))
                .when(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.PURCHASE_ORDER, ORDER_ID);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new PurchaseOrderSubmitRequest("gate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购订单必须上传附件");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        verify(purchaseOrderQueryService, never()).selectLines(any());
        verifyNoInteractions(purchasePriceEvaluator);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void submitStopsAtPriceGateBeforeStatusMutation() {
        PurchaseOrderEntity draft = order("REJECTED", "REJECTED", "NOT_RECEIVED");
        List<PurchaseOrderLineEntity> lines = List.of(
                line(4201L, "1.0000", "99.0000", "13.0000", "too expensive")
        );
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(purchaseOrderQueryService.selectLines(draft)).thenReturn(lines);
        doThrow(new IllegalArgumentException("第 1 行单价高于生效最高价"))
                .when(purchasePriceEvaluator)
                .assertLinesWithinMaxPrice(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service().submit(ORDER_ID, new PurchaseOrderSubmitRequest("retry")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("第 1 行单价高于生效最高价");

        assertThat(draft.getStatus()).isEqualTo("REJECTED");
        assertThat(draft.getApprovalStatus()).isEqualTo("REJECTED");
        verify(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.PURCHASE_ORDER, ORDER_ID);
        verify(purchaseOrderQueryService).selectLines(draft);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void submitOptimisticLockConflictDoesNotLoadDetailOrStartWorkflow() {
        PurchaseOrderEntity draft = order("DRAFT", "NOT_SUBMITTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(draft);
        when(purchaseOrderQueryService.selectLines(draft)).thenReturn(List.of(
                line(4201L, "1.0000", "10.0000", "13.0000", "line")
        ));
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(purchaseOrderMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().submit(ORDER_ID, new PurchaseOrderSubmitRequest("conflict")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("采购订单已被其他操作修改，请刷新后重试");

        assertThat(draft.getStatus()).isEqualTo("SUBMITTED");
        assertThat(draft.getApprovalStatus()).isEqualTo("IN_APPROVAL");
        assertAuditFields(draft);
        verify(purchaseOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    @Test
    void submitStopsWhenScopedOrderLookupIsDenied() {
        when(purchaseOrderQueryService.requireOrder(ORDER_ID))
                .thenThrow(new AccessDeniedException("无权访问采购订单"));

        assertThatThrownBy(() -> service().submit(ORDER_ID, new PurchaseOrderSubmitRequest("denied")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问采购订单");

        verify(purchaseOrderQueryService, never()).selectLines(any());
        verifyNoInteractions(attachmentService, purchasePriceEvaluator);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void directApproveReturnsUpdatedDetailBeforeCallingBusinessWorkflow() {
        PurchaseOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("APPROVED", "APPROVED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubSuccessfulTransition(submitted, expected);

        PurchaseOrderResponse actual = service().approve(
                ORDER_ID,
                new PurchaseOrderApproveRequest("direct approve")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("APPROVED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("APPROVED");
        assertAuditFields(submitted);
        verifyTransitionBeforeWorkflow(
                submitted,
                order -> order.verify(workflowService)
                        .approve("PURCHASE_ORDER", ORDER_ID, "direct approve")
        );
        verify(workflowService, never()).approveTaskForBusiness(any(), any(), any(), any());
    }

    @Test
    void taskApproveRoutesOnlyToMatchingWorkflowTask() {
        PurchaseOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("APPROVED", "APPROVED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubSuccessfulTransition(submitted, expected);

        PurchaseOrderResponse actual = service().approveWorkflowTask(
                WORKFLOW_TASK_ID,
                ORDER_ID,
                new PurchaseOrderApproveRequest("task approve")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("APPROVED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("APPROVED");
        assertAuditFields(submitted);
        verifyTransitionBeforeWorkflow(
                submitted,
                order -> order.verify(workflowService).approveTaskForBusiness(
                        WORKFLOW_TASK_ID,
                        "PURCHASE_ORDER",
                        ORDER_ID,
                        "task approve"
                )
        );
        verify(workflowService, never()).approve(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "SUBMITTED, APPROVED",
            "APPROVED, IN_APPROVAL"
    })
    void approveRejectsInvalidLifecycleCombination(String status, String approvalStatus) {
        PurchaseOrderEntity order = order(status, approvalStatus, "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().approve(
                ORDER_ID,
                new PurchaseOrderApproveRequest("invalid approve")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前采购订单状态不允许审批通过");

        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getApprovalStatus()).isEqualTo(approvalStatus);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void directRejectReturnsUpdatedDetailBeforeCallingBusinessWorkflow() {
        PurchaseOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("REJECTED", "REJECTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubSuccessfulTransition(submitted, expected);

        PurchaseOrderResponse actual = service().reject(
                ORDER_ID,
                new PurchaseOrderRejectRequest("price needs revision")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("REJECTED");
        assertAuditFields(submitted);
        verifyTransitionBeforeWorkflow(
                submitted,
                order -> order.verify(workflowService)
                        .reject("PURCHASE_ORDER", ORDER_ID, "price needs revision")
        );
        verify(workflowService, never()).rejectTaskForBusiness(any(), any(), any(), any());
    }

    @Test
    void taskRejectRoutesOnlyToMatchingWorkflowTask() {
        PurchaseOrderEntity submitted = order("SUBMITTED", "IN_APPROVAL", "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("REJECTED", "REJECTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubSuccessfulTransition(submitted, expected);

        PurchaseOrderResponse actual = service().rejectWorkflowTask(
                WORKFLOW_TASK_ID,
                ORDER_ID,
                new PurchaseOrderRejectRequest("task reject")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("REJECTED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("REJECTED");
        assertAuditFields(submitted);
        verifyTransitionBeforeWorkflow(
                submitted,
                order -> order.verify(workflowService).rejectTaskForBusiness(
                        WORKFLOW_TASK_ID,
                        "PURCHASE_ORDER",
                        ORDER_ID,
                        "task reject"
                )
        );
        verify(workflowService, never()).reject(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, NOT_SUBMITTED",
            "SUBMITTED, APPROVED",
            "REJECTED, IN_APPROVAL"
    })
    void rejectRejectsInvalidLifecycleCombination(String status, String approvalStatus) {
        PurchaseOrderEntity order = order(status, approvalStatus, "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().reject(
                ORDER_ID,
                new PurchaseOrderRejectRequest("invalid reject")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前采购订单状态不允许驳回");

        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getApprovalStatus()).isEqualTo(approvalStatus);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void unapproveAllowsOnlyCompletelyUnreceivedOrder() {
        PurchaseOrderEntity approved = order("APPROVED", "APPROVED", "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("DRAFT", "NOT_SUBMITTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        stubSuccessfulTransition(approved, expected);

        PurchaseOrderResponse actual = service().unapprove(ORDER_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(approved.getStatus()).isEqualTo("DRAFT");
        assertThat(approved.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        assertAuditFields(approved);
        verifyTransitionAndDetailOrder(approved);
        verifyNoInteractions(workflowService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL_RECEIVED", "RECEIVED"})
    void unapproveRejectsAnyReceivedOrderBeforeMutation(String receiptStatus) {
        PurchaseOrderEntity approved = order("APPROVED", "APPROVED", receiptStatus);
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);

        assertThatThrownBy(() -> service().unapprove(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已入库采购订单不允许反审核");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApprovalStatus()).isEqualTo("APPROVED");
        verifyNoTransitionOrWorkflow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "REJECTED", "SUBMITTED"})
    void cancelAllowsConfiguredLifecycleStatesThenCancelsWorkflow(String status) {
        String approvalStatus = switch (status) {
            case "REJECTED" -> "REJECTED";
            case "SUBMITTED" -> "IN_APPROVAL";
            default -> "NOT_SUBMITTED";
        };
        PurchaseOrderEntity submitted = order(status, approvalStatus, "NOT_RECEIVED");
        PurchaseOrderResponse expected = response("CANCELLED", "CANCELLED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(submitted);
        stubSuccessfulTransition(submitted, expected);

        PurchaseOrderResponse actual = service().cancel(ORDER_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(submitted.getStatus()).isEqualTo("CANCELLED");
        assertThat(submitted.getApprovalStatus()).isEqualTo("CANCELLED");
        assertAuditFields(submitted);
        verifyTransitionBeforeWorkflow(
                submitted,
                order -> order.verify(workflowService)
                        .cancel("PURCHASE_ORDER", ORDER_ID, "作废采购订单")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "CANCELLED", "CLOSED"})
    void cancelRejectsNonCancelableLifecycleStates(String status) {
        PurchaseOrderEntity order = order(status, "APPROVED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().cancel(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前采购订单状态不允许作废");

        assertThat(order.getStatus()).isEqualTo(status);
        verifyNoTransitionOrWorkflow();
    }

    @Test
    void closeAllowsPartiallyReceivedApprovedOrderWithoutWorkflowCall() {
        PurchaseOrderEntity approved = order("APPROVED", "APPROVED", "PARTIAL_RECEIVED");
        PurchaseOrderResponse expected = response("CLOSED", "APPROVED", "PARTIAL_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(approved);
        stubSuccessfulTransition(approved, expected);

        PurchaseOrderResponse actual = service().close(ORDER_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(approved.getStatus()).isEqualTo("CLOSED");
        assertThat(approved.getApprovalStatus()).isEqualTo("APPROVED");
        assertAuditFields(approved);
        verifyTransitionAndDetailOrder(approved);
        verifyNoInteractions(workflowService);
    }

    @Test
    void closeRejectsFullyReceivedOrderBeforeMutation() {
        PurchaseOrderEntity received = order("APPROVED", "APPROVED", "RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(received);

        assertThatThrownBy(() -> service().close(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已完全入库的采购订单不允许关闭");

        assertThat(received.getStatus()).isEqualTo("APPROVED");
        assertThat(received.getApprovalStatus()).isEqualTo("APPROVED");
        verifyNoTransitionOrWorkflow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "REJECTED", "CLOSED"})
    void closeRejectsNonApprovedLifecycleStates(String status) {
        PurchaseOrderEntity order = order(status, "NOT_SUBMITTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.requireOrder(ORDER_ID)).thenReturn(order);

        assertThatThrownBy(() -> service().close(ORDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前采购订单状态不允许关闭");

        assertThat(order.getStatus()).isEqualTo(status);
        verifyNoTransitionOrWorkflow();
    }

    private PurchaseOrderWorkflowService service() {
        return new PurchaseOrderWorkflowService(
                purchaseOrderMapper,
                auditMetadataFactory,
                purchaseOrderQueryService,
                workflowService,
                purchasePriceEvaluator,
                attachmentService
        );
    }

    private void stubSuccessfulTransition(PurchaseOrderEntity entity, PurchaseOrderResponse response) {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(purchaseOrderMapper.updateById(entity)).thenReturn(1);
        when(purchaseOrderQueryService.getById(ORDER_ID)).thenReturn(response);
    }

    private void assertAuditFields(PurchaseOrderEntity entity) {
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    private void verifyTransitionAndDetailOrder(PurchaseOrderEntity entity) {
        InOrder transitionOrder = inOrder(
                purchaseOrderQueryService,
                auditMetadataFactory,
                purchaseOrderMapper
        );
        transitionOrder.verify(purchaseOrderQueryService).requireOrder(ORDER_ID);
        transitionOrder.verify(auditMetadataFactory).current();
        transitionOrder.verify(purchaseOrderMapper).updateById(same(entity));
        transitionOrder.verify(purchaseOrderQueryService).getById(ORDER_ID);
    }

    private void verifyTransitionBeforeWorkflow(
            PurchaseOrderEntity entity,
            Consumer<InOrder> workflowVerification
    ) {
        InOrder transitionOrder = inOrder(
                purchaseOrderQueryService,
                auditMetadataFactory,
                purchaseOrderMapper,
                workflowService
        );
        transitionOrder.verify(purchaseOrderQueryService).requireOrder(ORDER_ID);
        transitionOrder.verify(auditMetadataFactory).current();
        transitionOrder.verify(purchaseOrderMapper).updateById(same(entity));
        transitionOrder.verify(purchaseOrderQueryService).getById(ORDER_ID);
        workflowVerification.accept(transitionOrder);
    }

    private void verifyNoTransitionOrWorkflow() {
        verifyNoInteractions(auditMetadataFactory);
        verify(purchaseOrderMapper, never()).updateById(any(PurchaseOrderEntity.class));
        verify(purchaseOrderQueryService, never()).getById(any());
        verifyNoInteractions(workflowService);
    }

    private PurchaseOrderEntity order(String status, String approvalStatus, String receiptStatus) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOrderNo("PO-4301");
        entity.setSupplierId(SUPPLIER_ID);
        entity.setOrderDate(ORDER_DATE);
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        entity.setReceiptStatus(receiptStatus);
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderLineEntity line(
            Long productId,
            String qty,
            String price,
            String taxRate,
            String remark
    ) {
        PurchaseOrderLineEntity line = new PurchaseOrderLineEntity();
        line.setProductId(productId);
        line.setQty(new BigDecimal(qty));
        line.setAuxQty(new BigDecimal("9.0000"));
        line.setAuxUnitName("box");
        line.setConversionFactor(new BigDecimal("10.0000"));
        line.setPrice(new BigDecimal(price));
        line.setTaxRate(new BigDecimal(taxRate));
        line.setRemark(remark);
        return line;
    }

    private PurchaseOrderResponse response(String status, String approvalStatus, String receiptStatus) {
        return new PurchaseOrderResponse(
                ORDER_ID,
                "PO-4301",
                SUPPLIER_ID,
                "tenant supplier",
                ORDER_DATE,
                LocalDate.of(2026, 6, 9),
                status,
                approvalStatus,
                receiptStatus,
                null,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "workflow test",
                List.of()
        );
    }
}

package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderNumberService;
import com.tuowei.erp.purchase.order.service.PurchasePriceEvaluator;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderWorkflowService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderServiceDecompositionTest {

    @Test
    void purchaseOrderServiceKeepsQueryTraceAndWorkflowBehindDedicatedServices() {
        assertThat(constructorDependencies(PurchaseOrderService.class))
                .contains(
                        PurchaseOrderLineMapper.class,
                        PurchaseOrderQueryService.class,
                        PurchaseOrderTraceService.class,
                        PurchaseOrderWorkflowService.class
                )
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        PurchaseReceiptMapper.class,
                        PurchaseReturnMapper.class,
                        PayableMapper.class,
                        PaymentAllocationMapper.class,
                        PaymentMapper.class,
                        VoucherMapper.class,
                        WorkflowService.class,
                        AttachmentService.class
                );
        assertThat(constructorDependencies(PurchaseOrderQueryService.class))
                .contains(PurchaseOrderLineMapper.class)
                .doesNotContain(PurchaseOrderService.class, PurchaseOrderWorkflowService.class);
        assertThat(constructorDependencies(PurchaseOrderWorkflowService.class))
                .contains(
                        PurchaseOrderMapper.class,
                        AuditMetadataFactory.class,
                        PurchaseOrderQueryService.class,
                        WorkflowService.class,
                        PurchasePriceEvaluator.class,
                        AttachmentService.class
                )
                .doesNotContain(
                        PurchaseOrderService.class,
                        PurchaseOrderLineMapper.class,
                        SupplierMapper.class,
                        ProductValidator.class,
                        PurchaseOrderNumberService.class,
                        PurchaseOrderTraceService.class
                );
    }

    @Test
    void workflowStateMachineKeepsRequiredTransactionsOnFacadeAndCollaborator() throws NoSuchMethodException {
        assertWorkflowTransactions(PurchaseOrderService.class);
        assertWorkflowTransactions(PurchaseOrderWorkflowService.class);
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertWorkflowTransactions(Class<?> type) throws NoSuchMethodException {
        assertRequiredWriteTransaction(type.getDeclaredMethod(
                "submit",
                Long.class,
                PurchaseOrderSubmitRequest.class
        ));
        assertRequiredWriteTransaction(type.getDeclaredMethod(
                "approve",
                Long.class,
                PurchaseOrderApproveRequest.class
        ));
        assertRequiredWriteTransaction(type.getDeclaredMethod(
                "approveWorkflowTask",
                Long.class,
                Long.class,
                PurchaseOrderApproveRequest.class
        ));
        assertRequiredWriteTransaction(type.getDeclaredMethod("unapprove", Long.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod(
                "reject",
                Long.class,
                PurchaseOrderRejectRequest.class
        ));
        assertRequiredWriteTransaction(type.getDeclaredMethod(
                "rejectWorkflowTask",
                Long.class,
                Long.class,
                PurchaseOrderRejectRequest.class
        ));
        assertRequiredWriteTransaction(type.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("close", Long.class));
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

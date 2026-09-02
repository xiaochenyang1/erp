package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.service.SalesOrderCommandService;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.service.SalesOrderWorkflowService;
import com.tuowei.erp.sales.order.service.SalesCreditEvaluator;
import com.tuowei.erp.sales.order.service.SalesPriceEvaluator;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.service.SalesOrderNumberService;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderServiceDecompositionTest {

    @Test
    void salesOrderServiceKeepsReadSideSecurityBehindQueryService() {
        Set<Class<?>> constructorDependencies = constructorDependencies(SalesOrderService.class);

        assertThat(constructorDependencies)
                .contains(SalesOrderQueryService.class, SalesOrderCommandService.class, SalesOrderWorkflowService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        InventoryPostingService.class
                );
        assertThat(constructorDependencies(SalesOrderQueryService.class))
                .doesNotContain(SalesOrderService.class);
        assertThat(constructorDependencies(SalesOrderCommandService.class))
                .contains(
                        SalesOrderMapper.class,
                        SalesOrderLineMapper.class,
                        CustomerMapper.class,
                        ProductValidator.class,
                        WarehouseMapper.class,
                        SalesOrderNumberService.class,
                        AuditMetadataFactory.class,
                        SalesOrderQueryService.class,
                        SalesCreditEvaluator.class,
                        SalesPriceEvaluator.class
                )
                .doesNotContain(SalesOrderService.class, SalesOrderWorkflowService.class);
        assertThat(constructorDependencies(SalesOrderWorkflowService.class))
                .contains(
                        CustomerMapper.class,
                        InventoryPostingService.class,
                        SalesOrderMapper.class,
                        AuditMetadataFactory.class,
                        SalesOrderQueryService.class,
                        WorkflowService.class,
                        SalesCreditEvaluator.class,
                        SalesPriceEvaluator.class,
                        AttachmentService.class
                )
                .doesNotContain(
                        SalesOrderService.class,
                        SalesOrderLineMapper.class,
                        ProductValidator.class,
                        SalesOrderNumberService.class
                );
    }

    @Test
    void orderWriteKeepsRequiredWriteTransactionOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SalesOrderService.class.getDeclaredMethod("create", com.tuowei.erp.sales.order.web.SalesOrderCreateRequest.class));
        assertRequiredWriteTransaction(SalesOrderCommandService.class.getDeclaredMethod("create", com.tuowei.erp.sales.order.web.SalesOrderCreateRequest.class));
        assertRequiredWriteTransaction(SalesOrderService.class.getDeclaredMethod("update", Long.class, com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest.class));
        assertRequiredWriteTransaction(SalesOrderCommandService.class.getDeclaredMethod("update", Long.class, com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest.class));
        assertReadOnlyTransaction(SalesOrderService.class.getDeclaredMethod("previewCredit", com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest.class));
        assertReadOnlyTransaction(SalesOrderCommandService.class.getDeclaredMethod("previewCredit", com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest.class));
        assertWorkflowTransactions(SalesOrderService.class);
        assertWorkflowTransactions(SalesOrderWorkflowService.class);
    }

    private void assertWorkflowTransactions(Class<?> type) throws NoSuchMethodException {
        assertRequiredWriteTransaction(type.getDeclaredMethod("submit", Long.class, SalesOrderSubmitRequest.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("approve", Long.class, SalesOrderApproveRequest.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("approveWorkflowTask", Long.class, Long.class, SalesOrderApproveRequest.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("reject", Long.class, SalesOrderRejectRequest.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("rejectWorkflowTask", Long.class, Long.class, SalesOrderRejectRequest.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("unapprove", Long.class));
        assertRequiredWriteTransaction(type.getDeclaredMethod("cancel", Long.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private void assertReadOnlyTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

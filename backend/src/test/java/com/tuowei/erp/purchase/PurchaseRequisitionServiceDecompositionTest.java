package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionCommandService;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionQueryService;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionService;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionCreateRequest;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionPageQuery;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionUpdateRequest;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PurchaseRequisitionServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(PurchaseRequisitionService.class))
                .containsExactlyInAnyOrder(PurchaseRequisitionQueryService.class, PurchaseRequisitionCommandService.class);
        assertThat(constructorDependencies(PurchaseRequisitionQueryService.class))
                .containsExactlyInAnyOrder(
                        PurchaseRequisitionMapper.class,
                        PurchaseRequisitionLineMapper.class,
                        ProductMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(PurchaseRequisitionService.class, PurchaseRequisitionCommandService.class);
        assertThat(constructorDependencies(PurchaseRequisitionCommandService.class))
                .containsExactlyInAnyOrder(
                        PurchaseRequisitionMapper.class,
                        PurchaseRequisitionLineMapper.class,
                        SupplierMapper.class,
                        PurchaseOrderService.class,
                        SequenceNumberGenerator.class,
                        WorkflowService.class,
                        AuditMetadataFactory.class,
                        AttachmentService.class,
                        PurchaseRequisitionQueryService.class
                )
                .doesNotContain(PurchaseRequisitionService.class, ProductMapper.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        PurchaseRequisitionQueryService queryService = mock(PurchaseRequisitionQueryService.class);
        PurchaseRequisitionCommandService commandService = mock(PurchaseRequisitionCommandService.class);
        PurchaseRequisitionService service = new PurchaseRequisitionService(queryService, commandService);
        PurchaseRequisitionCreateRequest createRequest = null;
        PurchaseRequisitionUpdateRequest updateRequest = null;

        service.create(createRequest);
        service.update(10L, updateRequest);
        service.submit(10L);
        service.approve(10L);
        service.approveWorkflowTask(20L, 10L, "approve");
        service.reject(10L);
        service.rejectWorkflowTask(20L, 10L, "reject");
        service.cancel(10L);
        service.convertToPurchaseOrder(10L);
        service.getById(10L);
        service.list(null);

        verify(commandService).create(createRequest);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).submit(10L);
        verify(commandService).approve(10L);
        verify(commandService).approveWorkflowTask(20L, 10L, "approve");
        verify(commandService).reject(10L);
        verify(commandService).rejectWorkflowTask(20L, 10L, "reject");
        verify(commandService).cancel(10L);
        verify(commandService).convertToPurchaseOrder(10L);
        verify(queryService).getById(10L);
        verify(queryService).list(any(PurchaseRequisitionPageQuery.class));
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionContracts() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{PurchaseRequisitionService.class, PurchaseRequisitionQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
            assertReadOnly(type.getDeclaredMethod("list", PurchaseRequisitionPageQuery.class));
        }
        for (Class<?> type : new Class<?>[]{PurchaseRequisitionService.class, PurchaseRequisitionCommandService.class}) {
            assertRequired(type.getDeclaredMethod("create", PurchaseRequisitionCreateRequest.class));
            assertRequired(type.getDeclaredMethod("update", Long.class, PurchaseRequisitionUpdateRequest.class));
            assertRequired(type.getDeclaredMethod("submit", Long.class));
            assertRequired(type.getDeclaredMethod("approve", Long.class));
            assertRequired(type.getDeclaredMethod("approveWorkflowTask", Long.class, Long.class, String.class));
            assertRequired(type.getDeclaredMethod("reject", Long.class));
            assertRequired(type.getDeclaredMethod("rejectWorkflowTask", Long.class, Long.class, String.class));
            assertRequired(type.getDeclaredMethod("cancel", Long.class));
            assertRequired(type.getDeclaredMethod("convertToPurchaseOrder", Long.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequired(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

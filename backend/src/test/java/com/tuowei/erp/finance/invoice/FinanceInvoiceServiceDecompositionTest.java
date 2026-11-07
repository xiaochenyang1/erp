package com.tuowei.erp.finance.invoice;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.service.FinanceInvoiceCommandService;
import com.tuowei.erp.finance.invoice.service.FinanceInvoiceQueryService;
import com.tuowei.erp.finance.invoice.service.FinanceInvoiceService;
import com.tuowei.erp.finance.invoice.service.InvoiceNumberService;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoicePageQuery;
import com.tuowei.erp.finance.invoice.web.InvoiceUpdateRequest;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
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

class FinanceInvoiceServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(FinanceInvoiceService.class))
                .containsExactlyInAnyOrder(FinanceInvoiceQueryService.class, FinanceInvoiceCommandService.class);
        assertThat(constructorDependencies(FinanceInvoiceQueryService.class))
                .containsExactlyInAnyOrder(InvoiceRegisterMapper.class, AuditMetadataFactory.class)
                .doesNotContain(FinanceInvoiceService.class, FinanceInvoiceCommandService.class);
        assertThat(constructorDependencies(FinanceInvoiceCommandService.class))
                .containsExactlyInAnyOrder(
                        InvoiceRegisterMapper.class, InvoiceNumberService.class, PurchaseOrderMapper.class,
                        SalesOrderMapper.class, AuditMetadataFactory.class, AttachmentService.class,
                        FinanceInvoiceQueryService.class
                )
                .doesNotContain(FinanceInvoiceService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        FinanceInvoiceQueryService queryService = mock(FinanceInvoiceQueryService.class);
        FinanceInvoiceCommandService commandService = mock(FinanceInvoiceCommandService.class);
        FinanceInvoiceService service = new FinanceInvoiceService(queryService, commandService);
        InvoiceCreateRequest createRequest = null;
        InvoiceUpdateRequest updateRequest = null;
        service.create(createRequest);
        service.list(null);
        service.detail(10L);
        service.update(10L, updateRequest);
        service.post(10L);
        service.cancel(10L);
        verify(commandService).create(createRequest);
        verify(queryService).list(any(InvoicePageQuery.class));
        verify(queryService).detail(10L);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).post(10L);
        verify(commandService).cancel(10L);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionContracts() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{FinanceInvoiceService.class, FinanceInvoiceQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", InvoicePageQuery.class));
            assertReadOnly(type.getDeclaredMethod("detail", Long.class));
        }
        for (Class<?> type : new Class<?>[]{FinanceInvoiceService.class, FinanceInvoiceCommandService.class}) {
            assertRequired(type.getDeclaredMethod("create", InvoiceCreateRequest.class));
            assertRequired(type.getDeclaredMethod("update", Long.class, InvoiceUpdateRequest.class));
            assertRequired(type.getDeclaredMethod("post", Long.class));
            assertRequired(type.getDeclaredMethod("cancel", Long.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors()).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet());
    }
    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.isAnnotationPresent(Autowired.class)).flatMap(c -> Arrays.stream(c.getParameterTypes())).collect(Collectors.toSet());
    }
    private void assertReadOnly(Method method) {
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isTrue();
    }
    private void assertRequired(Method method) {
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull(); assertThat(tx.readOnly()).isFalse(); assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

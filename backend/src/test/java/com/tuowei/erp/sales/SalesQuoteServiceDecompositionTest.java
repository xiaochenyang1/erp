package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteLineMapper;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteMapper;
import com.tuowei.erp.sales.quote.service.SalesQuoteCommandService;
import com.tuowei.erp.sales.quote.service.SalesQuoteNumberService;
import com.tuowei.erp.sales.quote.service.SalesQuoteQueryService;
import com.tuowei.erp.sales.quote.service.SalesQuoteService;
import com.tuowei.erp.sales.quote.web.SalesQuotePageQuery;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SalesQuoteServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(SalesQuoteService.class))
                .containsExactlyInAnyOrder(SalesQuoteQueryService.class, SalesQuoteCommandService.class);
        assertThat(constructorDependencies(SalesQuoteQueryService.class))
                .containsExactlyInAnyOrder(
                        SalesQuoteMapper.class,
                        SalesQuoteLineMapper.class,
                        CustomerMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(SalesQuoteService.class, SalesQuoteCommandService.class);
        assertThat(constructorDependencies(SalesQuoteCommandService.class))
                .containsExactlyInAnyOrder(
                        SalesQuoteMapper.class,
                        SalesQuoteLineMapper.class,
                        SalesQuoteNumberService.class,
                        CustomerMapper.class,
                        ProductValidator.class,
                        SalesOrderService.class,
                        AuditMetadataFactory.class,
                        SalesQuoteQueryService.class
                )
                .doesNotContain(SalesQuoteService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        SalesQuoteQueryService queryService = mock(SalesQuoteQueryService.class);
        SalesQuoteCommandService commandService = mock(SalesQuoteCommandService.class);
        SalesQuoteService service = new SalesQuoteService(queryService, commandService);
        SalesQuoteSaveRequest request = new SalesQuoteSaveRequest(10L, LocalDate.of(2026, 8, 22), null, null, java.util.List.of());

        service.create(request);
        service.update(1L, request);
        service.detail(1L);
        service.list(null);
        service.confirm(1L);
        service.cancel(1L);
        service.convertToOrder(1L, 2L);

        verify(commandService).create(request);
        verify(commandService).update(1L, request);
        verify(commandService).confirm(1L);
        verify(commandService).cancel(1L);
        verify(commandService).convertToOrder(1L, 2L);
        verify(queryService).detail(1L);
        verify(queryService).list(any(SalesQuotePageQuery.class));
    }

    @Test
    void readMethodsAreReadOnlyOnFacadeAndQuery() throws NoSuchMethodException {
        assertReadOnly(SalesQuoteService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(SalesQuoteService.class.getDeclaredMethod("list", SalesQuotePageQuery.class));
        assertReadOnly(SalesQuoteQueryService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(SalesQuoteQueryService.class.getDeclaredMethod("list", SalesQuotePageQuery.class));
    }

    @Test
    void commandMethodsKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SalesQuoteService.class, SalesQuoteCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", SalesQuoteSaveRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, SalesQuoteSaveRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("confirm", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("cancel", Long.class));
        }
        assertRequiredWrite(SalesQuoteService.class.getDeclaredMethod("convertToOrder", Long.class, Long.class));
        assertRequiredWrite(SalesQuoteCommandService.class.getDeclaredMethod("convertToOrder", Long.class, Long.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

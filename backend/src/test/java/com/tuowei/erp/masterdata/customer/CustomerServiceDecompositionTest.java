package com.tuowei.erp.masterdata.customer;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.service.CustomerCommandService;
import com.tuowei.erp.masterdata.customer.service.CustomerQueryService;
import com.tuowei.erp.masterdata.customer.service.CustomerService;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceDecompositionTest {

    @Test
    void facadeExposesQueryAndCommandConstructorWithoutReverseDependencies() {
        assertThat(constructorDependencies(CustomerService.class))
                .containsExactlyInAnyOrder(CustomerQueryService.class, CustomerCommandService.class);
        assertThat(constructorDependencies(CustomerQueryService.class))
                .containsExactlyInAnyOrder(CustomerMapper.class, AuditMetadataFactory.class)
                .doesNotContain(CustomerService.class, CustomerCommandService.class);
        assertThat(constructorDependencies(CustomerCommandService.class))
                .containsExactlyInAnyOrder(
                        CustomerMapper.class,
                        AuditMetadataFactory.class,
                        CustomerQueryService.class
                )
                .doesNotContain(CustomerService.class);
    }

    @Test
    void facadeDelegatesPublicApiAndNormalizesNullListQuery() {
        CustomerQueryService queryService = mock(CustomerQueryService.class);
        CustomerCommandService commandService = mock(CustomerCommandService.class);
        CustomerService service = new CustomerService(queryService, commandService);
        CustomerCreateRequest createRequest = createRequest();
        CustomerUpdateRequest updateRequest = updateRequest();
        CustomerPageQuery exportQuery = new CustomerPageQuery();
        StreamingResponseBody export = outputStream -> { };
        when(queryService.exportCustomers(exportQuery)).thenReturn(export);

        service.create(createRequest);
        service.getById(10L);
        service.list(null);
        assertThat(service.exportCustomers(exportQuery)).isSameAs(export);
        service.update(10L, updateRequest);
        service.enable(10L);
        service.disable(10L);

        verify(commandService).create(createRequest);
        verify(queryService).getById(10L);
        verify(queryService).list(any(CustomerPageQuery.class));
        verify(queryService).exportCustomers(exportQuery);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionsWhileExportOwnsNoTransaction()
            throws NoSuchMethodException {
        Class<?>[] queryServices = {CustomerService.class, CustomerQueryService.class};
        for (Class<?> serviceType : queryServices) {
            assertReadOnly(serviceType.getDeclaredMethod("getById", Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("list", CustomerPageQuery.class));
            assertThat(serviceType.getDeclaredMethod("exportCustomers", CustomerPageQuery.class)
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] commandServices = {CustomerService.class, CustomerCommandService.class};
        for (Class<?> serviceType : commandServices) {
            assertRequiredWrite(serviceType.getDeclaredMethod("create", CustomerCreateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("update", Long.class, CustomerUpdateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("disable", Long.class));
        }
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

    private CustomerCreateRequest createRequest() {
        return new CustomerCreateRequest(
                "C-10",
                "客户",
                "COMPANY",
                null,
                null,
                null,
                "MONTHLY",
                BigDecimal.TEN,
                30,
                null,
                null,
                null
        );
    }

    private CustomerUpdateRequest updateRequest() {
        return new CustomerUpdateRequest(
                "客户更新",
                "COMPANY",
                null,
                null,
                null,
                "MONTHLY",
                BigDecimal.TEN,
                30,
                null,
                "ACTIVE",
                null
        );
    }
}

package com.tuowei.erp.masterdata.supplier;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.service.SupplierCommandService;
import com.tuowei.erp.masterdata.supplier.service.SupplierQueryService;
import com.tuowei.erp.masterdata.supplier.service.SupplierService;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(SupplierService.class))
                .containsExactlyInAnyOrder(SupplierQueryService.class, SupplierCommandService.class);
        assertThat(constructorDependencies(SupplierQueryService.class))
                .containsExactlyInAnyOrder(SupplierMapper.class, AuditMetadataFactory.class)
                .doesNotContain(SupplierService.class, SupplierCommandService.class);
        assertThat(constructorDependencies(SupplierCommandService.class))
                .containsExactlyInAnyOrder(
                        SupplierMapper.class,
                        AuditMetadataFactory.class,
                        SupplierQueryService.class
                )
                .doesNotContain(SupplierService.class);
    }

    @Test
    void facadeDelegatesPublicApi() {
        SupplierQueryService queryService = mock(SupplierQueryService.class);
        SupplierCommandService commandService = mock(SupplierCommandService.class);
        SupplierService service = new SupplierService(queryService, commandService);
        SupplierCreateRequest createRequest = createRequest();
        SupplierUpdateRequest updateRequest = updateRequest();
        SupplierPageQuery query = new SupplierPageQuery();
        StreamingResponseBody export = outputStream -> { };
        when(queryService.exportSuppliers(query)).thenReturn(export);

        service.create(createRequest);
        service.getById(10L);
        service.list(null);
        assertThat(service.exportSuppliers(query)).isSameAs(export);
        service.update(10L, updateRequest);
        service.enable(10L);
        service.disable(10L);

        verify(commandService).create(createRequest);
        verify(queryService).getById(10L);
        verify(queryService).list(any(SupplierPageQuery.class));
        verify(queryService).exportSuppliers(query);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionsWhileExportOwnsNoTransaction()
            throws NoSuchMethodException {
        Class<?>[] queryServices = {SupplierService.class, SupplierQueryService.class};
        for (Class<?> serviceType : queryServices) {
            assertReadOnly(serviceType.getDeclaredMethod("getById", Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("list", SupplierPageQuery.class));
            assertThat(serviceType.getDeclaredMethod("exportSuppliers", SupplierPageQuery.class)
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] commandServices = {SupplierService.class, SupplierCommandService.class};
        for (Class<?> serviceType : commandServices) {
            assertRequiredWrite(serviceType.getDeclaredMethod("create", SupplierCreateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("update", Long.class, SupplierUpdateRequest.class));
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

    private SupplierCreateRequest createRequest() {
        return new SupplierCreateRequest(
                "S-10", "供应商", null, null, null, "MONTHLY", -2, null, null, null
        );
    }

    private SupplierUpdateRequest updateRequest() {
        return new SupplierUpdateRequest(
                "供应商更新", null, null, null, "MONTHLY", -1, null, "ACTIVE", null
        );
    }
}

package com.tuowei.erp.masterdata.customerproduct.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationRequest;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerProductRelationServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(CustomerProductRelationService.class))
                .containsExactlyInAnyOrder(
                        CustomerProductRelationQueryService.class,
                        CustomerProductRelationCommandService.class
                );
        assertThat(constructorDependencies(CustomerProductRelationCommandService.class))
                .contains(CustomerProductRelationQueryService.class)
                .doesNotContain(CustomerProductRelationService.class);
        assertThat(constructorDependencies(CustomerProductRelationQueryService.class))
                .doesNotContain(
                        CustomerProductRelationService.class,
                        CustomerProductRelationCommandService.class
                );
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        CustomerProductRelationQueryService queryService = mock(CustomerProductRelationQueryService.class);
        CustomerProductRelationCommandService commandService = mock(CustomerProductRelationCommandService.class);
        CustomerProductRelationService service = new CustomerProductRelationService(queryService, commandService);

        CustomerProductRelationRequest saveRequest = mock(CustomerProductRelationRequest.class);
        CustomerProductRelationResponse response = mock(CustomerProductRelationResponse.class);
        CustomerProductRelationEntity relation = mock(CustomerProductRelationEntity.class);
        AuditMetadata metadata = mock(AuditMetadata.class);
        @SuppressWarnings("unchecked")
        List<CustomerProductRelationResponse> listResponse = mock(List.class);

        when(queryService.list(404L)).thenReturn(listResponse);
        when(commandService.save(404L, saveRequest)).thenReturn(response);
        when(queryService.find(404L, 505L, metadata)).thenReturn(relation);

        assertThat(service.list(404L)).isSameAs(listResponse);
        assertThat(service.save(404L, saveRequest)).isSameAs(response);
        service.delete(404L, 606L);
        assertThat(service.find(404L, 505L, metadata)).isSameAs(relation);

        verify(queryService).list(404L);
        verify(commandService).save(404L, saveRequest);
        verify(commandService).delete(404L, 606L);
        verify(queryService).find(404L, 505L, metadata);
    }

    @Test
    void facadeAndQueryServiceKeepListReadOnly() throws NoSuchMethodException {
        assertReadOnly(CustomerProductRelationService.class.getDeclaredMethod("list", Long.class));
        assertReadOnly(CustomerProductRelationQueryService.class.getDeclaredMethod("list", Long.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{CustomerProductRelationService.class, CustomerProductRelationCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("save", Long.class, CustomerProductRelationRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("delete", Long.class, Long.class));
        }
    }

    @Test
    void facadeAndQueryServiceKeepFindNonTransactionalForReuse() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{CustomerProductRelationService.class, CustomerProductRelationQueryService.class}) {
            Method find = type.getDeclaredMethod("find", Long.class, Long.class, AuditMetadata.class);
            assertThat(find.getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeDeleteReturnsWithoutTouchingQueryService() {
        CustomerProductRelationQueryService queryService = mock(CustomerProductRelationQueryService.class);
        CustomerProductRelationCommandService commandService = mock(CustomerProductRelationCommandService.class);
        CustomerProductRelationService service = new CustomerProductRelationService(queryService, commandService);

        service.delete(404L, 606L);

        verify(commandService).delete(404L, 606L);
        verifyNoInteractions(queryService);
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

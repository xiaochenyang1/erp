package com.tuowei.erp.masterdata.supplierproduct.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.supplierproduct.model.SupplierProductRelationEntity;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationRequest;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationResponse;
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

class SupplierProductRelationServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(SupplierProductRelationService.class))
                .containsExactlyInAnyOrder(
                        SupplierProductRelationQueryService.class,
                        SupplierProductRelationCommandService.class
                );
        assertThat(constructorDependencies(SupplierProductRelationCommandService.class))
                .contains(SupplierProductRelationQueryService.class)
                .doesNotContain(SupplierProductRelationService.class);
        assertThat(constructorDependencies(SupplierProductRelationQueryService.class))
                .doesNotContain(
                        SupplierProductRelationService.class,
                        SupplierProductRelationCommandService.class
                );
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        SupplierProductRelationQueryService queryService = mock(SupplierProductRelationQueryService.class);
        SupplierProductRelationCommandService commandService = mock(SupplierProductRelationCommandService.class);
        SupplierProductRelationService service = new SupplierProductRelationService(queryService, commandService);

        SupplierProductRelationRequest saveRequest = mock(SupplierProductRelationRequest.class);
        SupplierProductRelationResponse response = mock(SupplierProductRelationResponse.class);
        SupplierProductRelationEntity relation = mock(SupplierProductRelationEntity.class);
        AuditMetadata metadata = mock(AuditMetadata.class);
        @SuppressWarnings("unchecked")
        List<SupplierProductRelationResponse> listResponse = mock(List.class);

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
        assertReadOnly(SupplierProductRelationService.class.getDeclaredMethod("list", Long.class));
        assertReadOnly(SupplierProductRelationQueryService.class.getDeclaredMethod("list", Long.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SupplierProductRelationService.class, SupplierProductRelationCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("save", Long.class, SupplierProductRelationRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("delete", Long.class, Long.class));
        }
    }

    @Test
    void facadeAndQueryServiceKeepFindNonTransactionalForPurchaseReuse() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SupplierProductRelationService.class, SupplierProductRelationQueryService.class}) {
            Method find = type.getDeclaredMethod("find", Long.class, Long.class, AuditMetadata.class);
            assertThat(find.getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeDeleteReturnsWithoutTouchingQueryService() {
        SupplierProductRelationQueryService queryService = mock(SupplierProductRelationQueryService.class);
        SupplierProductRelationCommandService commandService = mock(SupplierProductRelationCommandService.class);
        SupplierProductRelationService service = new SupplierProductRelationService(queryService, commandService);

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

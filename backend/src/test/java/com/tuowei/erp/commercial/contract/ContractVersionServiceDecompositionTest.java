package com.tuowei.erp.commercial.contract;

import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractVersionMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.service.ContractNumberService;
import com.tuowei.erp.commercial.contract.service.ContractQueryService;
import com.tuowei.erp.commercial.contract.service.ContractVersionCommandService;
import com.tuowei.erp.commercial.contract.service.ContractVersionQueryService;
import com.tuowei.erp.commercial.contract.service.ContractVersionService;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionResponse;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.when;

class ContractVersionServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(ContractVersionService.class))
                .containsExactlyInAnyOrder(ContractVersionQueryService.class, ContractVersionCommandService.class);
        assertThat(constructorDependencies(ContractVersionQueryService.class))
                .containsExactlyInAnyOrder(ContractVersionMapper.class, ContractQueryService.class, ObjectMapper.class)
                .doesNotContain(ContractVersionService.class, ContractVersionCommandService.class);
        assertThat(constructorDependencies(ContractVersionCommandService.class))
                .containsExactlyInAnyOrder(
                        ContractVersionMapper.class,
                        ContractMapper.class,
                        ContractLineMapper.class,
                        ContractQueryService.class,
                        ContractNumberService.class,
                        AuditMetadataFactory.class,
                        ObjectMapper.class,
                        ContractVersionQueryService.class
                )
                .doesNotContain(ContractVersionService.class);
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        ContractVersionQueryService queryService = mock(ContractVersionQueryService.class);
        ContractVersionCommandService commandService = mock(ContractVersionCommandService.class);
        ContractVersionService service = new ContractVersionService(queryService, commandService);

        ContractEntity contract = mock(ContractEntity.class);
        List<ContractLineEntity> lines = List.of(mock(ContractLineEntity.class));
        List<ContractVersionResponse> listResponse = List.of(mock(ContractVersionResponse.class));
        ContractVersionResponse detailResponse = mock(ContractVersionResponse.class);
        ContractResponse restoredResponse = mock(ContractResponse.class);

        when(queryService.list(9L)).thenReturn(listResponse);
        when(queryService.detail(9L, 3L)).thenReturn(detailResponse);
        when(commandService.restoreAsDraft(9L, 3L)).thenReturn(restoredResponse);

        service.record(contract, lines, "EDITED");
        assertThat(service.list(9L)).isSameAs(listResponse);
        assertThat(service.detail(9L, 3L)).isSameAs(detailResponse);
        assertThat(service.restoreAsDraft(9L, 3L)).isSameAs(restoredResponse);

        verify(commandService).record(contract, lines, "EDITED");
        verify(queryService).list(9L);
        verify(queryService).detail(9L, 3L);
        verify(commandService).restoreAsDraft(9L, 3L);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ContractVersionService.class, ContractVersionQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", Long.class));
            assertReadOnly(type.getDeclaredMethod("detail", Long.class, Long.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ContractVersionService.class, ContractVersionCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("record", ContractEntity.class, List.class, String.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("restoreAsDraft", Long.class, Long.class));
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

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

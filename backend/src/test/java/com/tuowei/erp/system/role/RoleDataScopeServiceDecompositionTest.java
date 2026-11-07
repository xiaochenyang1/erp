package com.tuowei.erp.system.role;

import com.tuowei.erp.system.role.service.RoleDataScopeCommandService;
import com.tuowei.erp.system.role.service.RoleDataScopeQueryService;
import com.tuowei.erp.system.role.service.RoleDataScopeService;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
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

class RoleDataScopeServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(RoleDataScopeService.class))
                .containsExactlyInAnyOrder(
                        RoleDataScopeQueryService.class,
                        RoleDataScopeCommandService.class
                );
        assertThat(constructorDependencies(RoleDataScopeCommandService.class))
                .contains(RoleDataScopeQueryService.class)
                .doesNotContain(RoleDataScopeService.class);
        assertThat(constructorDependencies(RoleDataScopeQueryService.class))
                .doesNotContain(RoleDataScopeService.class, RoleDataScopeCommandService.class);
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        RoleDataScopeQueryService queryService = mock(RoleDataScopeQueryService.class);
        RoleDataScopeCommandService commandService = mock(RoleDataScopeCommandService.class);
        RoleDataScopeService service = new RoleDataScopeService(queryService, commandService);

        RoleDataScopeResponse assigned = new RoleDataScopeResponse(3002L, false, true, false, false, List.of());
        RoleDataScopeResponse updated = new RoleDataScopeResponse(3002L, true, false, false, false, List.of());
        RoleDataScopeAssignRequest request =
                new RoleDataScopeAssignRequest(false, true, false, true, List.of(4501L));
        when(queryService.getAssigned(3002L)).thenReturn(assigned);
        when(commandService.assign(3002L, request)).thenReturn(updated);

        assertThat(service.getAssigned(3002L)).isSameAs(assigned);
        assertThat(service.assign(3002L, request)).isSameAs(updated);

        verify(queryService).getAssigned(3002L);
        verify(commandService).assign(3002L, request);
    }

    @Test
    void facadeAndQueryServiceKeepGetAssignedReadOnly() throws NoSuchMethodException {
        assertReadOnly(RoleDataScopeService.class.getDeclaredMethod("getAssigned", Long.class));
        assertReadOnly(RoleDataScopeQueryService.class.getDeclaredMethod("getAssigned", Long.class));
    }

    @Test
    void facadeAndCommandServiceKeepAssignAsRequiredWriteTransaction() throws NoSuchMethodException {
        assertRequiredWriteTransaction(RoleDataScopeService.class
                .getDeclaredMethod("assign", Long.class, RoleDataScopeAssignRequest.class));
        assertRequiredWriteTransaction(RoleDataScopeCommandService.class
                .getDeclaredMethod("assign", Long.class, RoleDataScopeAssignRequest.class));
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

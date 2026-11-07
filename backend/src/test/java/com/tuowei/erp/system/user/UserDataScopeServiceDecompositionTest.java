package com.tuowei.erp.system.user;

import com.tuowei.erp.system.user.service.UserDataScopeCommandService;
import com.tuowei.erp.system.user.service.UserDataScopeQueryService;
import com.tuowei.erp.system.user.service.UserDataScopeService;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
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

class UserDataScopeServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(UserDataScopeService.class))
                .containsExactlyInAnyOrder(
                        UserDataScopeQueryService.class,
                        UserDataScopeCommandService.class
                );
        assertThat(constructorDependencies(UserDataScopeCommandService.class))
                .contains(UserDataScopeQueryService.class)
                .doesNotContain(UserDataScopeService.class);
        assertThat(constructorDependencies(UserDataScopeQueryService.class))
                .doesNotContain(UserDataScopeService.class, UserDataScopeCommandService.class);
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        UserDataScopeQueryService queryService = mock(UserDataScopeQueryService.class);
        UserDataScopeCommandService commandService = mock(UserDataScopeCommandService.class);
        UserDataScopeService service = new UserDataScopeService(queryService, commandService);

        UserDataScopeResponse assigned = new UserDataScopeResponse(
                9001L, false, true, false, false, List.of(), false, true, false, false, List.of());
        UserDataScopeResponse updated = new UserDataScopeResponse(
                9001L, true, false, false, false, List.of(), true, false, false, false, List.of());
        UserDataScopeAssignRequest request =
                new UserDataScopeAssignRequest(false, true, false, true, List.of(4501L));
        when(queryService.getAssigned(9001L)).thenReturn(assigned);
        when(commandService.assign(9001L, request)).thenReturn(updated);

        assertThat(service.getAssigned(9001L)).isSameAs(assigned);
        assertThat(service.assign(9001L, request)).isSameAs(updated);

        verify(queryService).getAssigned(9001L);
        verify(commandService).assign(9001L, request);
    }

    @Test
    void facadeAndQueryServiceKeepGetAssignedReadOnly() throws NoSuchMethodException {
        assertReadOnly(UserDataScopeService.class.getDeclaredMethod("getAssigned", Long.class));
        assertReadOnly(UserDataScopeQueryService.class.getDeclaredMethod("getAssigned", Long.class));
    }

    @Test
    void facadeAndCommandServiceKeepAssignAsRequiredWriteTransaction() throws NoSuchMethodException {
        assertRequiredWriteTransaction(UserDataScopeService.class
                .getDeclaredMethod("assign", Long.class, UserDataScopeAssignRequest.class));
        assertRequiredWriteTransaction(UserDataScopeCommandService.class
                .getDeclaredMethod("assign", Long.class, UserDataScopeAssignRequest.class));
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

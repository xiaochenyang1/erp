package com.tuowei.erp.system.auth;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.auth.service.UserSessionCommandService;
import com.tuowei.erp.system.auth.service.UserSessionQueryService;
import com.tuowei.erp.system.auth.service.UserSessionService;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.auth.web.UserSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(UserSessionService.class))
                .containsExactlyInAnyOrder(
                        UserSessionQueryService.class,
                        UserSessionCommandService.class
                );
        assertThat(constructorDependencies(UserSessionCommandService.class))
                .doesNotContain(UserSessionService.class, UserSessionQueryService.class);
        assertThat(constructorDependencies(UserSessionQueryService.class))
                .doesNotContain(UserSessionService.class, UserSessionCommandService.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        UserSessionQueryService queryService = mock(UserSessionQueryService.class);
        UserSessionCommandService commandService = mock(UserSessionCommandService.class);
        UserSessionService service = new UserSessionService(queryService, commandService);

        UserSessionPageQuery query = mock(UserSessionPageQuery.class);
        PageResponse<UserSessionResponse> listed = mock(PageResponse.class);
        when(queryService.list(query)).thenReturn(listed);

        assertThat(service.list(query)).isSameAs(listed);
        service.revokeSession(9001L);
        service.revokeAllForUser(1001L);

        verify(queryService).list(query);
        verify(commandService).revokeSession(9001L);
        verify(commandService).revokeAllForUser(1001L);
    }

    @Test
    void facadeAndQueryServiceKeepListReadOnly() throws NoSuchMethodException {
        assertReadOnly(UserSessionService.class.getDeclaredMethod("list", UserSessionPageQuery.class));
        assertReadOnly(UserSessionQueryService.class.getDeclaredMethod("list", UserSessionPageQuery.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{UserSessionService.class, UserSessionCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("revokeSession", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("revokeAllForUser", Long.class));
        }
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

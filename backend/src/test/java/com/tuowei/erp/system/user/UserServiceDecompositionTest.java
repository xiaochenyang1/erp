package com.tuowei.erp.system.user;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.service.UserCommandService;
import com.tuowei.erp.system.user.service.UserQueryService;
import com.tuowei.erp.system.user.service.UserService;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(UserService.class))
                .containsExactlyInAnyOrder(UserQueryService.class, UserCommandService.class);
        assertThat(constructorDependencies(UserQueryService.class))
                .containsExactlyInAnyOrder(
                        UserMapper.class,
                        UserRoleMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(UserService.class, UserCommandService.class);
        assertThat(constructorDependencies(UserCommandService.class))
                .containsExactlyInAnyOrder(
                        UserMapper.class,
                        UserRoleMapper.class,
                        RoleMapper.class,
                        DeptMapper.class,
                        PostMapper.class,
                        PasswordEncoder.class,
                        AuditMetadataFactory.class,
                        RefreshTokenService.class,
                        SecurityPrincipalCache.class,
                        ScopedUserResolver.class,
                        UserPermissionService.class,
                        UserQueryService.class
                )
                .doesNotContain(UserService.class);
    }

    @Test
    void facadeDelegatesAllNineApisAndNormalizesNullListQuery() {
        UserQueryService queryService = mock(UserQueryService.class);
        UserCommandService commandService = mock(UserCommandService.class);
        UserService service = new UserService(queryService, commandService);
        UserCreateRequest createRequest = new UserCreateRequest(
                "alice", "Password12345", "EMP-1", "Alice", null, null, null, null, null, null
        );
        UserUpdateRequest updateRequest = new UserUpdateRequest(
                "EMP-1", "Alice Updated", null, null, null, null, null, null
        );
        UserRoleAssignRequest roleRequest = new UserRoleAssignRequest(List.of(301L, 302L));
        ResetPasswordRequest resetRequest = new ResetPasswordRequest("NewPassword12345");

        service.create(createRequest);
        service.list(null);
        service.getById(901L);
        service.getAssignedRoles(901L);
        service.update(901L, updateRequest);
        service.enable(901L);
        service.disable(901L);
        service.assignRoles(901L, roleRequest);
        service.resetPassword(901L, resetRequest);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(UserPageQuery.class));
        verify(queryService).getById(901L);
        verify(queryService).getAssignedRoles(901L);
        verify(commandService).update(901L, updateRequest);
        verify(commandService).enable(901L);
        verify(commandService).disable(901L);
        verify(commandService).assignRoles(901L, roleRequest);
        verify(commandService).resetPassword(901L, resetRequest);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{UserService.class, UserQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", UserPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
            assertReadOnly(type.getDeclaredMethod("getAssignedRoles", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{UserService.class, UserCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", UserCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, UserUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("assignRoles", Long.class, UserRoleAssignRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("resetPassword", Long.class, ResetPasswordRequest.class));
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
}

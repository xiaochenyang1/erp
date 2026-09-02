package com.tuowei.erp.system.menu;

import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.service.MenuCommandService;
import com.tuowei.erp.system.menu.service.MenuQueryService;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
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

class MenuServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(MenuService.class))
                .containsExactlyInAnyOrder(MenuQueryService.class, MenuCommandService.class);
        assertThat(constructorDependencies(MenuQueryService.class))
                .containsExactlyInAnyOrder(
                        MenuMapper.class,
                        CurrentUserContext.class,
                        UserRoleMapper.class,
                        RoleMapper.class,
                        RoleMenuMapper.class,
                        CacheService.class,
                        com.fasterxml.jackson.databind.ObjectMapper.class
                )
                .doesNotContain(MenuService.class, MenuCommandService.class, AuditMetadataFactory.class);
        assertThat(constructorDependencies(MenuCommandService.class))
                .containsExactlyInAnyOrder(
                        MenuMapper.class,
                        AuditMetadataFactory.class,
                        SecurityPrincipalCache.class,
                        UserPermissionService.class,
                        CacheService.class,
                        MenuQueryService.class
                )
                .doesNotContain(MenuService.class);
    }

    @Test
    void facadeDelegatesAllEightApisAndNormalizesNullListQuery() {
        MenuQueryService queryService = mock(MenuQueryService.class);
        MenuCommandService commandService = mock(MenuCommandService.class);
        MenuService service = new MenuService(queryService, commandService);
        MenuCreateRequest createRequest = new MenuCreateRequest(
                10L, "MENU", "SYSTEM_USER", "用户管理", "/system/users", "system/user", "system:user:view", 10
        );
        MenuUpdateRequest updateRequest = new MenuUpdateRequest(
                "用户管理（更新）", "/system/users", "system/user", "system:user:view", 20
        );

        service.create(createRequest);
        service.list(null);
        service.tree();
        service.runtimeTreeForCurrentUser();
        service.getById(11L);
        service.update(11L, updateRequest);
        service.enable(11L);
        service.disable(11L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(MenuPageQuery.class));
        verify(queryService).tree();
        verify(queryService).runtimeTreeForCurrentUser();
        verify(queryService).getById(11L);
        verify(commandService).update(11L, updateRequest);
        verify(commandService).enable(11L);
        verify(commandService).disable(11L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{MenuService.class, MenuQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", MenuPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("tree"));
            assertReadOnly(type.getDeclaredMethod("runtimeTreeForCurrentUser"));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{MenuService.class, MenuCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", MenuCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, MenuUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
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

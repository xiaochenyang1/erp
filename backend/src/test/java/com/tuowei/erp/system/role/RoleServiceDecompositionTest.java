package com.tuowei.erp.system.role;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.service.RoleCommandService;
import com.tuowei.erp.system.role.service.RoleQueryService;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoleServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(RoleService.class))
                .containsExactlyInAnyOrder(RoleQueryService.class, RoleCommandService.class);
        assertThat(constructorDependencies(RoleQueryService.class))
                .containsExactlyInAnyOrder(
                        RoleMapper.class,
                        MenuMapper.class,
                        RoleMenuMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(RoleService.class, RoleCommandService.class);
        assertThat(constructorDependencies(RoleCommandService.class))
                .containsExactlyInAnyOrder(
                        RoleMapper.class,
                        RoleMenuMapper.class,
                        AuditMetadataFactory.class,
                        SecurityPrincipalCache.class,
                        UserPermissionService.class,
                        RoleQueryService.class
                )
                .doesNotContain(RoleService.class);
    }

    @Test
    void facadeDelegatesAllEightApisAndNormalizesNullListQuery() {
        RoleQueryService queryService = mock(RoleQueryService.class);
        RoleCommandService commandService = mock(RoleCommandService.class);
        RoleService service = new RoleService(queryService, commandService);
        RoleCreateRequest createRequest = new RoleCreateRequest("FINANCE", "财务", "remark");
        RoleUpdateRequest updateRequest = new RoleUpdateRequest("财务更新", "updated");
        RoleMenuAssignRequest menuRequest = new RoleMenuAssignRequest(java.util.List.of(11L, 12L));

        service.create(createRequest);
        service.list(null);
        service.getById(7L);
        service.update(7L, updateRequest);
        service.enable(7L);
        service.disable(7L);
        service.assignMenus(7L, menuRequest);
        service.getAssignedMenus(7L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(RolePageQuery.class));
        verify(queryService).getById(7L);
        verify(commandService).update(7L, updateRequest);
        verify(commandService).enable(7L);
        verify(commandService).disable(7L);
        verify(commandService).assignMenus(7L, menuRequest);
        verify(queryService).getAssignedMenus(7L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{RoleService.class, RoleQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", RolePageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
            assertReadOnly(type.getDeclaredMethod("getAssignedMenus", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{RoleService.class, RoleCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", RoleCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, RoleUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("assignMenus", Long.class, RoleMenuAssignRequest.class));
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

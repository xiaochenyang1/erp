package com.tuowei.erp.system.menu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MenuServiceRuntimeTreeTest {

    private static final Long USER_ID = 8801L;
    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long ROLE_ID = 8901L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(UserRoleEntity.class);
        initTableInfo(RoleEntity.class);
    }

    @Test
    void runtimeTreeFiltersActiveRolesByCompanyAndAccountBook() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(Set.of("workflow:view")));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of());

        MenuService service = service(currentUserContext, userRoleMapper, roleMapper, mock(RoleMenuMapper.class), mock(MenuMapper.class));

        assertThat(service.runtimeTreeForCurrentUser()).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    @Test
    void runtimeTreeIncludesAssignedAncestorsOnly() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(Set.of("system:user:view", "workflow:view")));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(activeRole("TENANT_ADMIN")));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(5002L), roleMenu(6002L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(5001L, 0L, "CATALOG", "SYSTEM", "系统管理", "/system", null),
                menu(5002L, 5001L, "MENU", "SYSTEM_USER", "用户管理", "/system/users", "system:user:view"),
                menu(5003L, 5001L, "MENU", "SYSTEM_ROLE", "角色管理", "/system/roles", "system:role:view"),
                menu(6001L, 0L, "CATALOG", "WORKFLOW", "审批中心", "/workflow", null),
                menu(6002L, 6001L, "MENU", "WORKFLOW_TASK", "审批待办", "/workflow/tasks", "workflow:view")
        ));

        MenuService service = service(currentUserContext, userRoleMapper, roleMapper, roleMenuMapper, menuMapper);

        List<MenuResponse> tree = service.runtimeTreeForCurrentUser();

        assertThat(tree).extracting(MenuResponse::menuCode).containsExactly("SYSTEM", "WORKFLOW");
        assertThat(tree.get(0).children()).extracting(MenuResponse::menuCode).containsExactly("SYSTEM_USER");
        assertThat(tree.get(1).children()).extracting(MenuResponse::menuCode).containsExactly("WORKFLOW_TASK");
    }

    @Test
    void runtimeTreeReturnsAllMenusForSuperAdmin() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(Set.of("system:user:view", "system:role:view")));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole()));
        when(roleMapper.selectList(any())).thenReturn(List.of(activeRole("SUPER_ADMIN")));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(5001L, 0L, "CATALOG", "SYSTEM", "系统管理", "/system", null),
                menu(5002L, 5001L, "MENU", "SYSTEM_USER", "用户管理", "/system/users", "system:user:view"),
                menu(5003L, 5001L, "MENU", "SYSTEM_ROLE", "角色管理", "/system/roles", "system:role:view")
        ));

        MenuService service = service(currentUserContext, userRoleMapper, roleMapper, roleMenuMapper, menuMapper);

        List<MenuResponse> tree = service.runtimeTreeForCurrentUser();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).extracting(MenuResponse::menuCode)
                .containsExactly("SYSTEM_USER", "SYSTEM_ROLE");
        verify(roleMenuMapper, never()).selectList(any());
    }

    private static MenuService service(
            CurrentUserContext currentUserContext,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper
    ) {
        return new MenuService(
                menuMapper,
                mock(com.tuowei.erp.common.security.AuditMetadataFactory.class),
                mock(com.tuowei.erp.common.security.SecurityPrincipalCache.class),
                mock(UserPermissionService.class),
                currentUserContext,
                userRoleMapper,
                roleMapper,
                roleMenuMapper,
                CacheService.NOOP,
                new ObjectMapper()
        );
    }

    private static ErpPrincipal principal(Set<String> permissions) {
        return new ErpPrincipal(
                USER_ID,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                "runtime_menu_user",
                "运行时菜单用户",
                "N/A",
                permissions
        );
    }

    private static UserRoleEntity userRole() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(ROLE_ID);
        return entity;
    }

    private static RoleEntity activeRole(String roleCode) {
        RoleEntity entity = new RoleEntity();
        entity.setId(ROLE_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleCode);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static RoleMenuEntity roleMenu(Long menuId) {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(ROLE_ID);
        entity.setMenuId(menuId);
        return entity;
    }

    private static MenuEntity menu(
            Long id,
            Long parentId,
            String menuType,
            String menuCode,
            String menuName,
            String path,
            String permission
    ) {
        MenuEntity entity = new MenuEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setMenuType(menuType);
        entity.setMenuCode(menuCode);
        entity.setMenuName(menuName);
        entity.setPath(path);
        entity.setPermission(permission);
        entity.setSortNo(id.intValue());
        entity.setVisibleFlag(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

package com.tuowei.erp.system.menu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.menu.service.MenuQueryService;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MenuQueryServiceTest {

    private static final Long USER_ID = 8801L;
    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long ROLE_ID = 8901L;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(MenuEntity.class);
        initTableInfo(RoleEntity.class);
        initTableInfo(RoleMenuEntity.class);
        initTableInfo(UserRoleEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndMapsRows() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        Page<MenuEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(menu(5001L, 0L, "CATALOG", "SYSTEM", "系统管理", "/system", null)));
        when(menuMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        MenuPageQuery query = new MenuPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  system ");
        query.setStatus(" active ");
        query.setParentId(0L);
        query.setMenuType(" catalog ");

        MenuQueryService service = service(menuMapper, mock(CurrentUserContext.class),
                mock(UserRoleMapper.class), mock(RoleMapper.class), mock(RoleMenuMapper.class), CacheService.NOOP);

        var response = service.list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.menuCode()).isEqualTo("SYSTEM");
            assertThat(item.children()).isEmpty();
        });

        ArgumentCaptor<Page<MenuEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<MenuEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(menuMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .contains("menu_code")
                .contains("menu_name")
                .contains("path")
                .contains("permission")
                .contains("status")
                .contains("parent_id")
                .contains("menu_type");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("%system%", "ACTIVE", 0L, "CATALOG");
    }

    @Test
    void treeUsesGlobalCacheSnapshotWithoutReadingDatabase() throws Exception {
        MenuMapper menuMapper = mock(MenuMapper.class);
        CacheService cacheService = mock(CacheService.class);
        String cached = new ObjectMapper().writeValueAsString(List.of(
                menu(5001L, 0L, "CATALOG", "SYSTEM", "系统管理", "/system", null),
                menu(5002L, 5001L, "MENU", "SYSTEM_USER", "用户管理", "/system/users", "system:user:view")
        ));
        when(cacheService.getOrLoad(
                eq("erp:global:menu:all-active"),
                eq(Duration.ofMinutes(10)),
                any(Supplier.class)
        )).thenReturn(cached);

        MenuQueryService service = service(menuMapper, mock(CurrentUserContext.class),
                mock(UserRoleMapper.class), mock(RoleMapper.class), mock(RoleMenuMapper.class), cacheService);

        List<MenuResponse> tree = service.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).menuCode()).isEqualTo("SYSTEM");
        assertThat(tree.get(0).children()).extracting(MenuResponse::menuCode)
                .containsExactly("SYSTEM_USER");
        verify(menuMapper, never()).selectList(any());
        verify(cacheService).getOrLoad(eq("erp:global:menu:all-active"), eq(Duration.ofMinutes(10)), any(Supplier.class));
    }

    @Test
    void treeFallsBackToDatabaseWhenCachedPayloadCannotBeRead() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.getOrLoad(anyString(), eq(Duration.ofMinutes(10)), any(Supplier.class)))
                .thenReturn("{not-json");
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                menu(6001L, 0L, "CATALOG", "FALLBACK", "回退菜单", "/fallback", null)
        ));

        MenuQueryService service = service(menuMapper, mock(CurrentUserContext.class),
                mock(UserRoleMapper.class), mock(RoleMapper.class), mock(RoleMenuMapper.class), cacheService);

        assertThat(service.tree()).extracting(MenuResponse::menuCode).containsExactly("FALLBACK");
        verify(menuMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void getByIdMapsEntityAndRejectsDeletedMenu() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        MenuEntity entity = menu(7001L, 0L, "MENU", "DETAIL", "详情", "/detail", "detail:view");
        when(menuMapper.selectById(7001L)).thenReturn(entity);

        MenuQueryService service = service(menuMapper, mock(CurrentUserContext.class),
                mock(UserRoleMapper.class), mock(RoleMapper.class), mock(RoleMenuMapper.class), CacheService.NOOP);

        assertThat(service.getById(7001L).menuCode()).isEqualTo("DETAIL");

        entity.setDeletedFlag(1);
        assertThatThrownBy(() -> service.getById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("菜单不存在");
    }

    @Test
    void runtimeTreeScopesRolesToCurrentCompanyAndAccountBook() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        when(currentUserContext.requirePrincipal()).thenReturn(principal());
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole(ROLE_ID)));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        MenuQueryService service = service(menuMapper, currentUserContext, userRoleMapper, roleMapper,
                mock(RoleMenuMapper.class), CacheService.NOOP);

        assertThat(service.runtimeTreeForCurrentUser()).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<RoleEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "status", "deleted_flag");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(COMPANY_ID, ACCOUNT_BOOK_ID, "ACTIVE", 0);
        verify(menuMapper, never()).selectList(any());
    }

    @Test
    void runtimeTreeReturnsEveryActiveVisibleMenuForSuperAdmin() {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        when(currentUserContext.requirePrincipal()).thenReturn(principal());
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole(ROLE_ID)));
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role("SUPER_ADMIN")));
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                menu(8001L, 0L, "CATALOG", "SYSTEM", "系统", "/system", null),
                menu(8002L, 8001L, "MENU", "USER", "用户", "/system/user", "user:view")
        ));

        MenuQueryService service = service(menuMapper, currentUserContext, userRoleMapper, roleMapper,
                roleMenuMapper, CacheService.NOOP);

        assertThat(service.runtimeTreeForCurrentUser()).extracting(MenuResponse::menuCode)
                .containsExactly("SYSTEM");
        verify(roleMenuMapper, never()).selectList(any());
    }

    private MenuQueryService service(
            MenuMapper menuMapper,
            CurrentUserContext currentUserContext,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            CacheService cacheService
    ) {
        return new MenuQueryService(
                menuMapper,
                currentUserContext,
                userRoleMapper,
                roleMapper,
                roleMenuMapper,
                cacheService,
                new ObjectMapper()
        );
    }

    private ErpPrincipal principal() {
        return new ErpPrincipal(
                USER_ID,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                null,
                null,
                "menu-query-user",
                "菜单查询用户",
                "N/A",
                Set.of("system:menu:view"),
                DataScopeSnapshot.all()
        );
    }

    private UserRoleEntity userRole(Long roleId) {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(USER_ID);
        entity.setRoleId(roleId);
        return entity;
    }

    private RoleEntity role(String roleCode) {
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

    private MenuEntity menu(
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

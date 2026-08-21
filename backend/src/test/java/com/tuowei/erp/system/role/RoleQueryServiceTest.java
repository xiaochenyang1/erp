package com.tuowei.erp.system.role;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.service.RoleQueryService;
import com.tuowei.erp.system.role.web.RolePageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RoleQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 15, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(RoleEntity.class);
        initTableInfo(MenuEntity.class);
        initTableInfo(RoleMenuEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesCompanyAndAccountBook() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        Page<RoleEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(role(7L, AUDIT.companyId(), AUDIT.accountBookId())));
        when(roleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        RolePageQuery query = new RolePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  finance ");
        query.setStatus(" active ");

        RoleQueryService service = service(roleMapper, mock(MenuMapper.class), mock(RoleMenuMapper.class));
        var response = service.list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.roleCode()).isEqualTo("FINANCE_ADMIN");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });

        ArgumentCaptor<Page<RoleEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<RoleEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "deleted_flag", "role_code", "role_name", "status");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%finance%", "ACTIVE", 0);
    }

    @Test
    void getByIdRejectsDifferentTenantOrDeletedRole() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleQueryService service = service(roleMapper, mock(MenuMapper.class), mock(RoleMenuMapper.class));

        when(roleMapper.selectById(7L)).thenReturn(role(7L, AUDIT.companyId(), 999L));
        assertThatThrownBy(() -> service.getById(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");

        RoleEntity deleted = role(8L, AUDIT.companyId(), AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(roleMapper.selectById(8L)).thenReturn(deleted);
        assertThatThrownBy(() -> service.getById(8L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");
    }

    @Test
    void getAssignedMenusPreservesOrderFiltersGhostsAndRetainsDisabledMenus() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        when(roleMapper.selectById(7L)).thenReturn(role(7L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                binding(11L), binding(12L), binding(13L), binding(14L), binding(11L)
        ));
        when(menuMapper.selectById(11L)).thenReturn(activeMenu(11L));
        when(menuMapper.selectById(12L)).thenReturn(null);
        when(menuMapper.selectById(13L)).thenReturn(deletedMenu(13L));
        MenuEntity disabled = activeMenu(14L);
        disabled.setStatus("DISABLED");
        disabled.setVisibleFlag(0);
        when(menuMapper.selectById(14L)).thenReturn(disabled);

        var response = service(roleMapper, menuMapper, roleMenuMapper).getAssignedMenus(7L);

        assertThat(response.menuIds()).containsExactly(11L, 14L, 11L);
        ArgumentCaptor<LambdaQueryWrapper<RoleMenuEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roleMenuMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("role_id", "order by id asc");
    }

    @Test
    void publicQueriesMapResponsesAndRetainOnlyExistingMenus() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        when(roleMapper.selectById(7L)).thenReturn(role(7L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(binding(11L), binding(12L)));
        when(menuMapper.selectById(11L)).thenReturn(activeMenu(11L));
        when(menuMapper.selectById(12L)).thenReturn(null);
        assertThat(service(roleMapper, menuMapper, roleMenuMapper).getById(7L).roleCode())
                .isEqualTo("FINANCE_ADMIN");
        assertThat(service(roleMapper, menuMapper, roleMenuMapper).getAssignedMenus(7L).menuIds())
                .containsExactly(11L);
    }

    @Test
    void getAssignedMenusRetainsExistingDisabledOrHiddenMenusUntilDeleted() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        when(roleMapper.selectById(7L)).thenReturn(role(7L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                binding(21L), binding(22L)
        ));
        MenuEntity disabled = activeMenu(21L);
        disabled.setStatus("DISABLED");
        MenuEntity hidden = activeMenu(22L);
        hidden.setVisibleFlag(0);
        when(menuMapper.selectById(21L)).thenReturn(disabled);
        when(menuMapper.selectById(22L)).thenReturn(hidden);

        assertThat(service(roleMapper, menuMapper, roleMenuMapper).getAssignedMenus(7L).menuIds())
                .containsExactly(21L, 22L);
    }

    private RoleQueryService service(RoleMapper roleMapper, MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        when(auditFactory.current()).thenReturn(AUDIT);
        return new RoleQueryService(roleMapper, menuMapper, roleMenuMapper, auditFactory);
    }

    private RoleEntity role(Long id, Long companyId, Long accountBookId) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setRoleCode("FINANCE_ADMIN");
        entity.setRoleName("财务管理员");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("role query");
        entity.setVersion(0);
        return entity;
    }

    private RoleMenuEntity binding(Long menuId) {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(7L);
        entity.setMenuId(menuId);
        return entity;
    }

    private MenuEntity activeMenu(Long id) {
        MenuEntity entity = new MenuEntity();
        entity.setId(id);
        entity.setDeletedFlag(0);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private MenuEntity deletedMenu(Long id) {
        MenuEntity entity = activeMenu(id);
        entity.setDeletedFlag(1);
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

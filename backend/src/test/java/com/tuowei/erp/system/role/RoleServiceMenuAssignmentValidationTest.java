package com.tuowei.erp.system.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.service.RoleService;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceMenuAssignmentValidationTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @Test
    void assignMenusRejectsNullRequestBeforeChangingAssignments() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        RoleService service = service(roleMapper, mock(MenuMapper.class), roleMenuMapper);
        when(roleMapper.selectById(7L)).thenReturn(role());

        assertThatThrownBy(() -> service.assignMenus(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("menuIds不能为空");

        verify(roleMenuMapper, never()).delete(any());
        verify(roleMenuMapper, never()).insert(any(RoleMenuEntity.class));
    }

    @Test
    void assignMenusRejectsNullMenuIdsBeforeChangingAssignments() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        RoleService service = service(roleMapper, mock(MenuMapper.class), roleMenuMapper);
        when(roleMapper.selectById(7L)).thenReturn(role());

        assertThatThrownBy(() -> service.assignMenus(7L, new RoleMenuAssignRequest(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("menuIds不能为空");

        verify(roleMenuMapper, never()).delete(any());
        verify(roleMenuMapper, never()).insert(any(RoleMenuEntity.class));
    }

    @Test
    void assignMenusRejectsNullMenuIdBeforeQueryingMenusOrChangingAssignments() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        RoleService service = service(roleMapper, menuMapper, roleMenuMapper);
        when(roleMapper.selectById(7L)).thenReturn(role());

        assertThatThrownBy(() -> service.assignMenus(7L, new RoleMenuAssignRequest(Arrays.asList(11L, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("menuIds不能包含空值");

        verify(menuMapper, never()).selectById(any());
        verify(roleMenuMapper, never()).delete(any());
        verify(roleMenuMapper, never()).insert(any(RoleMenuEntity.class));
    }

    @Test
    void assignMenusSkipsMissingAndDeletedMenusAndPersistsActiveOnes() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        SecurityPrincipalCache principalCache = mock(SecurityPrincipalCache.class);
        UserPermissionService permissionService = mock(UserPermissionService.class);
        RoleService service = new RoleService(
                roleMapper, menuMapper, roleMenuMapper, auditFactory(), principalCache, permissionService
        );
        when(roleMapper.selectById(7L)).thenReturn(role());
        when(menuMapper.selectById(11L)).thenReturn(activeMenu(11L));
        when(menuMapper.selectById(12L)).thenReturn(null);
        when(menuMapper.selectById(13L)).thenReturn(deletedMenu(13L));
        when(menuMapper.selectById(14L)).thenReturn(activeMenu(14L));
        when(roleMenuMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(roleMenuMapper.insert(any(RoleMenuEntity.class))).thenReturn(1);

        RoleMenuAssignmentResponse response = service.assignMenus(
                7L, new RoleMenuAssignRequest(List.of(11L, 12L, 13L, 14L, 11L))
        );

        assertThat(response.menuIds()).containsExactly(11L, 14L);
        ArgumentCaptor<RoleMenuEntity> captor = ArgumentCaptor.forClass(RoleMenuEntity.class);
        verify(roleMenuMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoleMenuEntity::getMenuId).containsExactly(11L, 14L);
        verify(principalCache).evictAll();
        verify(permissionService).evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void assignMenusRejectsWhenAllRequestedMenusAreInactive() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        RoleService service = service(roleMapper, menuMapper, roleMenuMapper);
        when(roleMapper.selectById(7L)).thenReturn(role());
        when(menuMapper.selectById(12L)).thenReturn(null);
        when(menuMapper.selectById(13L)).thenReturn(deletedMenu(13L));

        assertThatThrownBy(() -> service.assignMenus(7L, new RoleMenuAssignRequest(List.of(12L, 13L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效菜单不能为空");

        verify(roleMenuMapper, never()).delete(any());
        verify(roleMenuMapper, never()).insert(any(RoleMenuEntity.class));
    }

    @Test
    void getAssignedMenusFiltersDeletedBindings() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        MenuMapper menuMapper = mock(MenuMapper.class);
        RoleMenuMapper roleMenuMapper = mock(RoleMenuMapper.class);
        RoleService service = service(roleMapper, menuMapper, roleMenuMapper);
        when(roleMapper.selectById(7L)).thenReturn(role());
        RoleMenuEntity bindActive = new RoleMenuEntity();
        bindActive.setMenuId(11L);
        RoleMenuEntity bindGhost = new RoleMenuEntity();
        bindGhost.setMenuId(99L);
        when(roleMenuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(bindActive, bindGhost));
        when(menuMapper.selectById(11L)).thenReturn(activeMenu(11L));
        when(menuMapper.selectById(99L)).thenReturn(null);

        RoleMenuAssignmentResponse response = service.getAssignedMenus(7L);

        assertThat(response.menuIds()).containsExactly(11L);
    }

    private static RoleService service(RoleMapper roleMapper, MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        return new RoleService(
                roleMapper,
                menuMapper,
                roleMenuMapper,
                auditFactory(),
                mock(SecurityPrincipalCache.class),
                mock(UserPermissionService.class)
        );
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private static RoleEntity role() {
        RoleEntity entity = new RoleEntity();
        entity.setId(7L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRoleCode("FINANCE_ADMIN");
        entity.setRoleName("财务管理员");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static MenuEntity activeMenu(Long id) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setDeletedFlag(0);
        menu.setStatus("ACTIVE");
        return menu;
    }

    private static MenuEntity deletedMenu(Long id) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setDeletedFlag(1);
        menu.setStatus("INACTIVE");
        return menu;
    }
}

package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 15, 30)
    );
    private static final Long ROLE_ID = 7L;

    @Mock private RoleMapper roleMapper;
    @Mock private RoleMenuMapper roleMenuMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SecurityPrincipalCache principalCache;
    @Mock private UserPermissionService userPermissionService;
    @Mock private RoleQueryService roleQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantAuditedActiveRoleWithoutCacheInvalidation() {
        when(roleMapper.insert(any(RoleEntity.class))).thenAnswer(invocation -> {
            RoleEntity entity = invocation.getArgument(0);
            entity.setId(ROLE_ID);
            return 1;
        });
        RoleResponse expected = response("ACTIVE");
        when(roleQueryService.toResponse(any(RoleEntity.class))).thenReturn(expected);

        RoleResponse actual = service().create(new RoleCreateRequest(" FINANCE_ADMIN ", " 财务管理员 ", "remark"));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
        verify(roleMapper).insert(captor.capture());
        RoleEntity entity = captor.getValue();
        assertThat(entity.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(entity.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(entity.getRoleCode()).isEqualTo(" FINANCE_ADMIN ");
        assertThat(entity.getRoleName()).isEqualTo(" 财务管理员 ");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getDeletedFlag()).isZero();
        assertThat(entity.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(entity.getVersion()).isZero();
        verifyNoInteractions(principalCache, userPermissionService);
    }

    @Test
    void updateAuditsRoleAndDoesNotClearCaches() {
        RoleEntity entity = role();
        when(roleQueryService.requireRole(ROLE_ID)).thenReturn(entity);
        when(roleMapper.updateById(entity)).thenReturn(1);
        when(roleQueryService.toResponse(entity)).thenReturn(response("ACTIVE"));

        service().update(ROLE_ID, new RoleUpdateRequest(" 财务更新 ", "updated"));

        assertThat(entity.getRoleName()).isEqualTo(" 财务更新 ");
        assertThat(entity.getRemark()).isEqualTo("updated");
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        verifyNoInteractions(principalCache, userPermissionService);
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeResponseOrCacheInvalidation() {
        RoleEntity entity = role();
        when(roleQueryService.requireRole(ROLE_ID)).thenReturn(entity);
        when(roleMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(ROLE_ID, new RoleUpdateRequest("冲突", null)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("角色已被其他操作修改，请刷新后重试");

        verify(roleQueryService, never()).toResponse(entity);
        verifyNoInteractions(principalCache, userPermissionService);
    }

    @Test
    void statusCommandsSetStatusAndClearPrincipalAndAccountBookPermissionCaches() {
        RoleEntity entity = role();
        when(roleQueryService.requireRole(ROLE_ID)).thenReturn(entity);
        when(roleMapper.updateById(entity)).thenReturn(1);
        when(roleQueryService.toResponse(entity)).thenReturn(response("ACTIVE"));

        service().disable(ROLE_ID);
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        service().enable(ROLE_ID);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        verify(principalCache, times(2)).evictAll();
        verify(userPermissionService, times(2))
                .evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void assignMenusDeduplicatesFiltersGhostMenusPersistsRowsAndInvalidatesCaches() {
        when(roleQueryService.requireRole(ROLE_ID)).thenReturn(role());
        when(roleQueryService.retainActiveMenuIds(List.of(11L, 12L, 13L)))
                .thenReturn(List.of(11L, 13L));
        when(roleMenuMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(roleMenuMapper.insert(any(RoleMenuEntity.class))).thenReturn(1);

        var response = service().assignMenus(ROLE_ID,
                new RoleMenuAssignRequest(List.of(11L, 12L, 11L, 13L)));

        assertThat(response.menuIds()).containsExactly(11L, 13L);
        verify(roleMenuMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<RoleMenuEntity> captor = ArgumentCaptor.forClass(RoleMenuEntity.class);
        verify(roleMenuMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoleMenuEntity::getMenuId)
                .containsExactly(11L, 13L);
        assertThat(captor.getAllValues()).allSatisfy(binding -> {
            assertThat(binding.getRoleId()).isEqualTo(ROLE_ID);
            assertThat(binding.getCreatedBy()).isEqualTo(AUDIT.userId());
            assertThat(binding.getCreatedTime()).isEqualTo(AUDIT.now());
        });
        verify(principalCache).evictAll();
        verify(userPermissionService)
                .evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void assignMenusRejectsNullOrAllGhostMenusBeforeDeletingBindings() {
        when(roleQueryService.requireRole(ROLE_ID)).thenReturn(role());

        assertThatThrownBy(() -> service().assignMenus(ROLE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("menuIds不能为空");
        assertThatThrownBy(() -> service().assignMenus(
                ROLE_ID,
                new RoleMenuAssignRequest(java.util.Arrays.asList(11L, null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("menuIds不能包含空值");

        when(roleQueryService.retainActiveMenuIds(List.of(12L))).thenReturn(List.of());
        assertThatThrownBy(() -> service().assignMenus(ROLE_ID, new RoleMenuAssignRequest(List.of(12L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效菜单不能为空");

        verify(roleMenuMapper, never()).delete(any());
        verifyNoInteractions(principalCache, userPermissionService);
    }

    @Test
    void assignMenusDoesNotClearCachesWhenRoleLookupFails() {
        when(roleQueryService.requireRole(ROLE_ID))
                .thenThrow(new IllegalArgumentException("角色不存在"));

        assertThatThrownBy(() -> service().assignMenus(ROLE_ID, new RoleMenuAssignRequest(List.of(11L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色不存在");

        verifyNoInteractions(roleMenuMapper, principalCache, userPermissionService);
    }

    private RoleCommandService service() {
        return new RoleCommandService(
                roleMapper,
                roleMenuMapper,
                auditMetadataFactory,
                principalCache,
                userPermissionService,
                roleQueryService
        );
    }

    private RoleEntity role() {
        return role(ROLE_ID, AUDIT.companyId(), AUDIT.accountBookId());
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
        entity.setVersion(0);
        return entity;
    }

    private RoleResponse response(String status) {
        return new RoleResponse(ROLE_ID, "FINANCE_ADMIN", "财务管理员", status, "remark");
    }
}

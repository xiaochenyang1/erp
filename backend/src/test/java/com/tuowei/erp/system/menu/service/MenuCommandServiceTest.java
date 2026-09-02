package com.tuowei.erp.system.menu.service;

import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            2L,
            102L,
            203L,
            LocalDateTime.of(2026, 8, 21, 12, 30)
    );
    private static final Long MENU_ID = 7001L;

    @Mock
    private MenuMapper menuMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private SecurityPrincipalCache principalCache;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private CacheService cacheService;
    @Mock
    private MenuQueryService menuQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsActiveAuditedEntityAndOnlyClearsPrincipalAndGlobalMenuCaches() {
        when(menuMapper.insert(any(MenuEntity.class))).thenAnswer(invocation -> {
            MenuEntity entity = invocation.getArgument(0);
            entity.setId(MENU_ID);
            return 1;
        });
        MenuResponse expected = response("SYSTEM_USER", "ACTIVE");
        when(menuQueryService.toResponse(any(MenuEntity.class))).thenReturn(expected);

        MenuResponse actual = service().create(new MenuCreateRequest(
                5001L,
                "MENU",
                "SYSTEM_USER",
                " 用户管理 ",
                "/system/users",
                "system/user",
                "system:user:view",
                null
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<MenuEntity> captor = ArgumentCaptor.forClass(MenuEntity.class);
        verify(menuMapper).insert(captor.capture());
        MenuEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(MENU_ID);
        assertThat(inserted.getParentId()).isEqualTo(5001L);
        assertThat(inserted.getMenuCode()).isEqualTo("SYSTEM_USER");
        assertThat(inserted.getMenuName()).isEqualTo(" 用户管理 ");
        assertThat(inserted.getSortNo()).isZero();
        assertThat(inserted.getVisibleFlag()).isEqualTo(1);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();

        verify(principalCache).evictAll();
        verify(cacheService).evict(CacheKeyBuilder.global("menu", "all-active"));
        verify(userPermissionService, never()).evictAccountBookPermissions(any(), any());
    }

    @Test
    void updateAuditsEntityUsesOptimisticLockAndClearsAccountBookPermissionCache() {
        MenuEntity entity = menu("SYSTEM_USER", "ACTIVE");
        when(menuQueryService.requireMenu(MENU_ID)).thenReturn(entity);
        when(menuMapper.updateById(entity)).thenReturn(1);
        MenuResponse expected = response("SYSTEM_USER", "ACTIVE");
        when(menuQueryService.toResponse(entity)).thenReturn(expected);

        MenuResponse actual = service().update(MENU_ID, new MenuUpdateRequest(
                " 用户管理（更新） ",
                "/system/users-v2",
                "system/user-v2",
                "system:user:updated",
                null
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(entity.getMenuName()).isEqualTo(" 用户管理（更新） ");
        assertThat(entity.getPath()).isEqualTo("/system/users-v2");
        assertThat(entity.getComponent()).isEqualTo("system/user-v2");
        assertThat(entity.getPermission()).isEqualTo("system:user:updated");
        assertThat(entity.getSortNo()).isZero();
        assertThat(entity.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(principalCache).evictAll();
        verify(cacheService).evict(CacheKeyBuilder.global("menu", "all-active"));
        verify(userPermissionService).evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void statusCommandsSetExpectedStatusAndShareUpdateCacheInvalidation() {
        MenuEntity entity = menu("SYSTEM_USER", "ACTIVE");
        when(menuQueryService.requireMenu(MENU_ID)).thenReturn(entity);
        when(menuMapper.updateById(entity)).thenReturn(1);
        when(menuQueryService.toResponse(entity)).thenReturn(response("SYSTEM_USER", "ACTIVE"));

        service().disable(MENU_ID);
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        service().enable(MENU_ID);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        verify(menuMapper, times(2)).updateById(entity);
        verify(principalCache, times(2)).evictAll();
        verify(cacheService, times(2)).evict(CacheKeyBuilder.global("menu", "all-active"));
        verify(userPermissionService, times(2))
                .evictAccountBookPermissions(AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void updateStopsAtOptimisticConflictBeforeCacheClearingOrResponseMapping() {
        MenuEntity entity = menu("SYSTEM_USER", "ACTIVE");
        when(menuQueryService.requireMenu(MENU_ID)).thenReturn(entity);
        when(menuMapper.updateById(entity)).thenReturn(0);

        assertThatThrownBy(() -> service().update(MENU_ID,
                new MenuUpdateRequest("冲突", "/conflict", null, null, 1)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("菜单已被其他操作修改，请刷新后重试");

        verify(principalCache, never()).evictAll();
        verify(cacheService, never()).evict(any());
        verify(userPermissionService, never()).evictAccountBookPermissions(any(), any());
        verify(menuQueryService, never()).toResponse(entity);
    }

    private MenuCommandService service() {
        return new MenuCommandService(
                menuMapper,
                auditMetadataFactory,
                principalCache,
                userPermissionService,
                cacheService,
                menuQueryService
        );
    }

    private MenuEntity menu(String code, String status) {
        MenuEntity entity = new MenuEntity();
        entity.setId(MENU_ID);
        entity.setParentId(5001L);
        entity.setMenuType("MENU");
        entity.setMenuCode(code);
        entity.setMenuName("用户管理");
        entity.setPath("/system/users");
        entity.setComponent("system/user");
        entity.setPermission("system:user:view");
        entity.setSortNo(3);
        entity.setVisibleFlag(1);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private MenuResponse response(String code, String status) {
        return new MenuResponse(
                MENU_ID,
                5001L,
                "MENU",
                code,
                "用户管理",
                "/system/users",
                "system/user",
                "system:user:view",
                3,
                1,
                status,
                java.util.List.of()
        );
    }
}

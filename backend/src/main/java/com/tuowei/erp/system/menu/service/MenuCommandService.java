package com.tuowei.erp.system.menu.service;

import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Write-side menu creation, updates, status commands and cache invalidation. */
@Service
public class MenuCommandService {

    private final MenuMapper menuMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final UserPermissionService userPermissionService;
    private final CacheService cacheService;
    private final MenuQueryService menuQueryService;

    public MenuCommandService(
            MenuMapper menuMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            UserPermissionService userPermissionService,
            CacheService cacheService,
            MenuQueryService menuQueryService
    ) {
        this.menuMapper = menuMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.userPermissionService = userPermissionService;
        this.cacheService = cacheService;
        this.menuQueryService = menuQueryService;
    }

    @Transactional
    public MenuResponse create(MenuCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        MenuEntity entity = new MenuEntity();
        entity.setParentId(request.parentId() == null ? 0L : request.parentId());
        entity.setMenuType(request.menuType());
        entity.setMenuCode(request.menuCode());
        entity.setMenuName(request.menuName());
        entity.setPath(request.path());
        entity.setComponent(request.component());
        entity.setPermission(request.permission());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setVisibleFlag(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        setCreateAudit(entity, audit, now);
        menuMapper.insert(entity);
        invalidatePrincipalAndMenuCache();
        return menuQueryService.toResponse(entity);
    }

    @Transactional
    public MenuResponse update(Long id, MenuUpdateRequest request) {
        MenuEntity entity = menuQueryService.requireMenu(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setMenuName(request.menuName());
        entity.setPath(request.path());
        entity.setComponent(request.component());
        entity.setPermission(request.permission());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(menuMapper.updateById(entity), "菜单已被其他操作修改，请刷新后重试");
        invalidatePrincipalAndMenuCache();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return menuQueryService.toResponse(entity);
    }

    @Transactional
    public MenuResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public MenuResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private MenuResponse updateStatus(Long id, String status) {
        MenuEntity entity = menuQueryService.requireMenu(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(menuMapper.updateById(entity), "菜单已被其他操作修改，请刷新后重试");
        invalidatePrincipalAndMenuCache();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return menuQueryService.toResponse(entity);
    }

    private void setCreateAudit(MenuEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void touch(MenuEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private void invalidatePrincipalAndMenuCache() {
        principalCache.evictAll();
        cacheService.evict(CacheKeyBuilder.global("menu", "all-active"));
    }
}

package com.tuowei.erp.system.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.web.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MenuService {

    private static final Duration ALL_MENUS_CACHE_TTL = Duration.ofMinutes(10);

    private final MenuMapper menuMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final UserPermissionService userPermissionService;
    private final CurrentUserContext currentUserContext;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public MenuService(
            MenuMapper menuMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            UserPermissionService userPermissionService,
            CurrentUserContext currentUserContext,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            CacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.menuMapper = menuMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.userPermissionService = userPermissionService;
        this.currentUserContext = currentUserContext;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
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
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        menuMapper.insert(entity);
        principalCache.evictAll();
        evictAllMenusCache();
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuResponse> list(MenuPageQuery query) {
        MenuPageQuery safeQuery = query == null ? new MenuPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        String menuType = normalizeMenuType(safeQuery.getMenuType());

        Page<MenuEntity> page = new Page<>(pageNo, pageSize);
        Page<MenuEntity> result = menuMapper.selectPage(page, buildListQuery(keyword, status, safeQuery.getParentId(), menuType));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> tree() {
        return buildTree(loadAllMenus());
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> runtimeTreeForCurrentUser() {
        ErpPrincipal principal = currentUserContext.requirePrincipal();
        List<RoleEntity> activeRoles = loadActiveRoles(principal);
        if (activeRoles.isEmpty()) {
            return List.of();
        }

        List<MenuEntity> runtimeMenus = selectRuntimeVisibleMenus(loadAllMenus());
        if (runtimeMenus.isEmpty()) {
            return List.of();
        }
        if (activeRoles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))) {
            return buildTree(runtimeMenus);
        }

        Set<Long> assignedMenuIds = loadAssignedMenuIds(activeRoles);
        if (assignedMenuIds.isEmpty()) {
            return List.of();
        }
        return buildTree(selectAssignedMenusWithAncestors(runtimeMenus, assignedMenuIds));
    }

    @Transactional(readOnly = true)
    public MenuResponse getById(Long id) {
        return toResponse(requireMenu(id));
    }

    @Transactional
    public MenuResponse update(Long id, MenuUpdateRequest request) {
        MenuEntity entity = requireMenu(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setMenuName(request.menuName());
        entity.setPath(request.path());
        entity.setComponent(request.component());
        entity.setPermission(request.permission());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(menuMapper.updateById(entity), "菜单已被其他操作修改，请刷新后重试");
        principalCache.evictAll();
        evictAllMenusCache();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return toResponse(entity);
    }

    @Transactional
    public MenuResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public MenuResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private MenuResponse toResponse(MenuEntity entity) {
        return new MenuResponse(
                entity.getId(),
                entity.getParentId(),
                entity.getMenuType(),
                entity.getMenuCode(),
                entity.getMenuName(),
                entity.getPath(),
                entity.getComponent(),
                entity.getPermission(),
                entity.getSortNo(),
                entity.getVisibleFlag(),
                entity.getStatus(),
                new ArrayList<>()
        );
    }

    private MenuResponse updateStatus(Long id, String status) {
        MenuEntity entity = requireMenu(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(menuMapper.updateById(entity), "菜单已被其他操作修改，请刷新后重试");
        principalCache.evictAll();
        evictAllMenusCache();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return toResponse(entity);
    }

    private List<MenuResponse> buildTree(List<MenuEntity> entities) {
        Map<Long, MenuResponse> nodeMap = new LinkedHashMap<>();
        List<MenuResponse> roots = new ArrayList<>();

        for (MenuEntity entity : entities) {
            nodeMap.put(entity.getId(), toResponse(entity));
        }

        for (MenuEntity entity : entities) {
            MenuResponse current = nodeMap.get(entity.getId());
            if (current == null) {
                continue;
            }
            if (entity.getParentId() == null || entity.getParentId() == 0L) {
                roots.add(current);
                continue;
            }

            MenuResponse parent = nodeMap.get(entity.getParentId());
            if (parent == null) {
                roots.add(current);
                continue;
            }
            parent.children().add(current);
        }

        return roots;
    }

    private MenuEntity requireMenu(Long id) {
        MenuEntity entity = menuMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("菜单不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<MenuEntity> buildListQuery(String keyword, String status, Long parentId, String menuType) {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(MenuEntity::getMenuCode, keyword)
                    .or()
                    .like(MenuEntity::getMenuName, keyword)
                    .or()
                    .like(MenuEntity::getPath, keyword)
                    .or()
                    .like(MenuEntity::getPermission, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(MenuEntity::getStatus, status);
        }
        if (parentId != null) {
            wrapper.eq(MenuEntity::getParentId, parentId);
        }
        if (StringUtils.hasText(menuType)) {
            wrapper.eq(MenuEntity::getMenuType, menuType);
        }
        return wrapper.orderByAsc(MenuEntity::getParentId)
                .orderByAsc(MenuEntity::getSortNo)
                .orderByAsc(MenuEntity::getId);
    }

    private List<MenuEntity> loadAllMenus() {
        try {
            String cached = cacheService.getOrLoad(
                    allMenusCacheKey(),
                    ALL_MENUS_CACHE_TTL,
                    this::serializeAllMenus
            );
            if (cached == null || cached.isBlank()) {
                return loadAllMenusFromDb();
            }
            return objectMapper.readValue(cached, new TypeReference<List<MenuEntity>>() {});
        } catch (Exception ex) {
            return loadAllMenusFromDb();
        }
    }

    private String serializeAllMenus() {
        try {
            return objectMapper.writeValueAsString(loadAllMenusFromDb());
        } catch (Exception ex) {
            throw new IllegalStateException("serialize menus cache failed", ex);
        }
    }

    private List<MenuEntity> loadAllMenusFromDb() {
        return menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getDeletedFlag, 0)
                .orderByAsc(MenuEntity::getParentId)
                .orderByAsc(MenuEntity::getSortNo)
                .orderByAsc(MenuEntity::getId));
    }

    private List<MenuEntity> selectRuntimeVisibleMenus(List<MenuEntity> allMenus) {
        Map<Long, MenuEntity> menuMap = new LinkedHashMap<>();
        for (MenuEntity entity : allMenus) {
            menuMap.put(entity.getId(), entity);
        }
        return allMenus.stream()
                .filter(entity -> hasCompleteRuntimeAncestorChain(entity, menuMap))
                .toList();
    }

    private boolean hasCompleteRuntimeAncestorChain(MenuEntity entity, Map<Long, MenuEntity> menuMap) {
        Set<Long> visited = new LinkedHashSet<>();
        MenuEntity current = entity;
        while (current != null) {
            if (!"ACTIVE".equals(current.getStatus()) || !Integer.valueOf(1).equals(current.getVisibleFlag())) {
                return false;
            }
            if (current.getId() == null || !visited.add(current.getId())) {
                return false;
            }
            Long parentId = current.getParentId();
            if (parentId == null || parentId == 0L) {
                return true;
            }
            current = menuMap.get(parentId);
        }
        return false;
    }

    private String allMenusCacheKey() {
        return CacheKeyBuilder.global("menu", "all-active");
    }

    private void evictAllMenusCache() {
        cacheService.evict(allMenusCacheKey());
    }

    private List<RoleEntity> loadActiveRoles(ErpPrincipal principal) {
        List<Long> assignedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, principal.userId()))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (assignedRoleIds.isEmpty()) {
            return List.of();
        }

        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getId, assignedRoleIds)
                .eq(RoleEntity::getCompanyId, principal.companyId())
                .eq(RoleEntity::getAccountBookId, principal.accountBookId())
                .eq(RoleEntity::getStatus, "ACTIVE")
                .eq(RoleEntity::getDeletedFlag, 0)
                .orderByAsc(RoleEntity::getId));
    }

    private Set<Long> loadAssignedMenuIds(List<RoleEntity> activeRoles) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .in(RoleMenuEntity::getRoleId, activeRoles.stream().map(RoleEntity::getId).toList())
                        .orderByAsc(RoleMenuEntity::getId))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<MenuEntity> selectAssignedMenusWithAncestors(List<MenuEntity> allMenus, Set<Long> assignedMenuIds) {
        Map<Long, MenuEntity> menuMap = new LinkedHashMap<>();
        for (MenuEntity entity : allMenus) {
            menuMap.put(entity.getId(), entity);
        }

        Set<Long> includedIds = new LinkedHashSet<>();
        for (Long menuId : assignedMenuIds) {
            Long currentId = menuId;
            while (currentId != null && currentId != 0L && includedIds.add(currentId)) {
                MenuEntity current = menuMap.get(currentId);
                if (current == null) {
                    break;
                }
                currentId = current.getParentId();
            }
        }

        return allMenus.stream()
                .filter(entity -> includedIds.contains(entity.getId()))
                .toList();
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeMenuType(String menuType) {
        String normalized = normalizeNullableText(menuType);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

}

package com.tuowei.erp.system.menu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-side menu queries, runtime authorization tree assembly and global cache access. */
@Service
public class MenuQueryService {

    private static final Duration ALL_MENUS_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<MenuEntity>> MENU_LIST_TYPE = new TypeReference<>() {
    };

    private final MenuMapper menuMapper;
    private final CurrentUserContext currentUserContext;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public MenuQueryService(
            MenuMapper menuMapper,
            CurrentUserContext currentUserContext,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            CacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.menuMapper = menuMapper;
        this.currentUserContext = currentUserContext;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuResponse> list(MenuPageQuery query) {
        MenuPageQuery safeQuery = query == null ? new MenuPageQuery() : query;
        Page<MenuEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<MenuEntity> result = menuMapper.selectPage(page, buildListQuery(
                normalizeNullableText(safeQuery.getKeyword()),
                normalizeStatus(safeQuery.getStatus()),
                safeQuery.getParentId(),
                normalizeMenuType(safeQuery.getMenuType())
        ));
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

    MenuEntity requireMenu(Long id) {
        MenuEntity entity = menuMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("菜单不存在");
        }
        return entity;
    }

    MenuResponse toResponse(MenuEntity entity) {
        return new MenuResponse(
                entity.getId(), entity.getParentId(), entity.getMenuType(), entity.getMenuCode(),
                entity.getMenuName(), entity.getPath(), entity.getComponent(), entity.getPermission(),
                entity.getSortNo(), entity.getVisibleFlag(), entity.getStatus(), new ArrayList<>()
        );
    }

    private LambdaQueryWrapper<MenuEntity> buildListQuery(
            String keyword, String status, Long parentId, String menuType
    ) {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(MenuEntity::getMenuCode, keyword)
                    .or().like(MenuEntity::getMenuName, keyword)
                    .or().like(MenuEntity::getPath, keyword)
                    .or().like(MenuEntity::getPermission, keyword));
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
            String cached = cacheService.getOrLoad(allMenusCacheKey(), ALL_MENUS_CACHE_TTL, this::serializeAllMenus);
            if (cached == null || cached.isBlank()) {
                return loadAllMenusFromDb();
            }
            return objectMapper.readValue(cached, MENU_LIST_TYPE);
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
            } else {
                parent.children().add(current);
            }
        }
        return roots;
    }

    private List<MenuEntity> selectRuntimeVisibleMenus(List<MenuEntity> allMenus) {
        Map<Long, MenuEntity> menuMap = new LinkedHashMap<>();
        for (MenuEntity entity : allMenus) {
            menuMap.put(entity.getId(), entity);
        }
        return allMenus.stream().filter(entity -> hasCompleteRuntimeAncestorChain(entity, menuMap)).toList();
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

    private List<RoleEntity> loadActiveRoles(ErpPrincipal principal) {
        List<Long> assignedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, principal.userId()))
                .stream().map(UserRoleEntity::getRoleId).filter(Objects::nonNull).distinct().toList();
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
                .stream().map(RoleMenuEntity::getMenuId).filter(Objects::nonNull)
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
        return allMenus.stream().filter(entity -> includedIds.contains(entity.getId())).toList();
    }

    private String allMenusCacheKey() {
        return CacheKeyBuilder.global("menu", "all-active");
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeMenuType(String menuType) {
        String normalized = normalizeNullableText(menuType);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

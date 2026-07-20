package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserPermissionService {

    private static final Duration PERMISSION_CACHE_TTL = Duration.ofSeconds(60);

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final CacheService cacheService;

    public UserPermissionService(
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper,
            CacheService cacheService
    ) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.cacheService = cacheService;
    }

    public Set<String> loadPermissions(Long userId, Long companyId, Long accountBookId) {
        String cached = cacheService.getOrLoad(permissionCacheKey(userId, companyId, accountBookId),
                PERMISSION_CACHE_TTL,
                () -> serialize(loadPermissionsFromDatabase(userId, companyId, accountBookId)));
        return deserialize(cached);
    }

    public void evictUserPermissions(Long userId, Long companyId, Long accountBookId) {
        cacheService.evict(permissionCacheKey(userId, companyId, accountBookId));
    }

    public void evictAccountBookPermissions(Long companyId, Long accountBookId) {
        cacheService.evictByPrefix(permissionCachePrefix(companyId, accountBookId));
    }

    private Set<String> loadPermissionsFromDatabase(Long userId, Long companyId, Long accountBookId) {
        List<Long> assignedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        if (assignedRoleIds.isEmpty()) {
            return Set.of();
        }

        List<RoleEntity> activeRoles = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getId, assignedRoleIds)
                .eq(RoleEntity::getCompanyId, companyId)
                .eq(RoleEntity::getAccountBookId, accountBookId)
                .eq(RoleEntity::getStatus, "ACTIVE")
                .eq(RoleEntity::getDeletedFlag, 0));
        if (activeRoles.isEmpty()) {
            return Set.of();
        }
        if (activeRoles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))) {
            return PermissionCodes.allPermissions();
        }

        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .in(RoleMenuEntity::getRoleId, activeRoles.stream().map(RoleEntity::getId).toList()))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .toList();
        if (menuIds.isEmpty()) {
            return Set.of();
        }

        Set<String> permissions = new LinkedHashSet<>();
        menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .in(MenuEntity::getId, menuIds)
                        .eq(MenuEntity::getStatus, "ACTIVE")
                        .eq(MenuEntity::getDeletedFlag, 0)
                        .orderByAsc(MenuEntity::getId))
                .stream()
                .map(MenuEntity::getPermission)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(permissions::add);
        return permissions;
    }

    private String permissionCacheKey(Long userId, Long companyId, Long accountBookId) {
        return CacheKeyBuilder.accountBookScoped(companyId, accountBookId, "permission", "user", String.valueOf(userId));
    }

    private String permissionCachePrefix(Long companyId, Long accountBookId) {
        return CacheKeyBuilder.accountBookScoped(companyId, accountBookId, "permission") + ":";
    }

    private String serialize(Set<String> permissions) {
        return String.join("\n", permissions);
    }

    private Set<String> deserialize(String cached) {
        if (!StringUtils.hasText(cached)) {
            return Set.of();
        }
        return cached.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}

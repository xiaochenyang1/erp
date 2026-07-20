package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleCreateRequest;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleMenuAssignRequest;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final UserPermissionService userPermissionService;

    public RoleService(RoleMapper roleMapper,
                        MenuMapper menuMapper,
                        RoleMenuMapper roleMenuMapper,
                        AuditMetadataFactory auditMetadataFactory,
                        SecurityPrincipalCache principalCache,
                        UserPermissionService userPermissionService) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.userPermissionService = userPermissionService;
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        RoleEntity entity = new RoleEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        roleMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> list(RolePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        RolePageQuery safeQuery = query == null ? new RolePageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<RoleEntity> page = new Page<>(pageNo, pageSize);
        Page<RoleEntity> result = roleMapper.selectPage(page, buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return toResponse(requireRole(id));
    }

    @Transactional
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        RoleEntity entity = requireRole(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setRoleName(request.roleName());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(roleMapper.updateById(entity), "角色已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    @Transactional
    public RoleResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public RoleResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    @Transactional
    public RoleMenuAssignmentResponse assignMenus(Long roleId, RoleMenuAssignRequest request) {
        requireRole(roleId);
        AuditMetadata audit = auditMetadataFactory.current();
        List<Long> requestedIds = normalizeMenuIds(request == null ? null : request.menuIds());
        // 历史 role_menu 可能残留已删菜单 id；复制角色/整批保存时跳过无效节点，避免整批 400
        List<Long> menuIds = retainActiveMenuIds(requestedIds);
        if (menuIds.isEmpty()) {
            throw new IllegalArgumentException("有效菜单不能为空（请求中的菜单均不存在或已删除）");
        }

        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>()
                .eq(RoleMenuEntity::getRoleId, roleId));

        LocalDateTime now = audit.now();
        for (Long menuId : menuIds) {
            RoleMenuEntity roleMenuEntity = new RoleMenuEntity();
            roleMenuEntity.setRoleId(roleId);
            roleMenuEntity.setMenuId(menuId);
            roleMenuEntity.setCreatedBy(audit.userId());
            roleMenuEntity.setCreatedTime(now);
            roleMenuMapper.insert(roleMenuEntity);
        }

        principalCache.evictAll();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return new RoleMenuAssignmentResponse(roleId, menuIds);
    }

    @Transactional(readOnly = true)
    public RoleMenuAssignmentResponse getAssignedMenus(Long roleId) {
        requireRole(roleId);
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .eq(RoleMenuEntity::getRoleId, roleId)
                        .orderByAsc(RoleMenuEntity::getId))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .toList();
        // 回读也过滤幽灵绑定，前端树勾选与复制角色时不再带回已删 id
        return new RoleMenuAssignmentResponse(roleId, retainActiveMenuIds(menuIds));
    }

    private RoleResponse toResponse(RoleEntity entity) {
        return new RoleResponse(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private RoleResponse updateStatus(Long id, String status) {
        RoleEntity entity = requireRole(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(roleMapper.updateById(entity), "角色已被其他操作修改，请刷新后重试");
        principalCache.evictAll();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return toResponse(entity);
    }

    private RoleEntity requireRole(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("角色不存在");
        }
        return entity;
    }

    private List<Long> normalizeMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            throw new IllegalArgumentException("menuIds不能为空");
        }
        if (menuIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("menuIds不能包含空值");
        }
        return new ArrayList<>(new LinkedHashSet<>(menuIds));
    }

    /**
     * 保留存在且未删除的菜单 id，顺序与入参一致。
     * 不存在/已删的 id 静默丢弃（用于历史脏绑定与角色复制场景）。
     */
    private List<Long> retainActiveMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        List<Long> active = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            if (menuId == null) {
                continue;
            }
            MenuEntity menuEntity = menuMapper.selectById(menuId);
            if (menuEntity != null
                    && menuEntity.getDeletedFlag() != null
                    && menuEntity.getDeletedFlag() == 0) {
                active.add(menuId);
            }
        }
        return active;
    }

    private LambdaQueryWrapper<RoleEntity> buildListQuery(Long companyId, Long accountBookId, String keyword, String status) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCompanyId, companyId)
                .eq(RoleEntity::getAccountBookId, accountBookId)
                .eq(RoleEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(RoleEntity::getRoleCode, keyword)
                    .or()
                    .like(RoleEntity::getRoleName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RoleEntity::getStatus, status);
        }
        return wrapper.orderByAsc(RoleEntity::getRoleCode);
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

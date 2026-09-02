package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
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
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RoleResponse;
import com.tuowei.erp.system.role.web.RoleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Write-side role lifecycle and menu assignment commands. */
@Service
public class RoleCommandService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final UserPermissionService userPermissionService;
    private final RoleQueryService roleQueryService;

    public RoleCommandService(
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            UserPermissionService userPermissionService,
            RoleQueryService roleQueryService
    ) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.userPermissionService = userPermissionService;
        this.roleQueryService = roleQueryService;
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
        return roleQueryService.toResponse(entity);
    }

    @Transactional
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        RoleEntity entity = roleQueryService.requireRole(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setRoleName(request.roleName());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(roleMapper.updateById(entity), "角色已被其他操作修改，请刷新后重试");
        return roleQueryService.toResponse(entity);
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
        roleQueryService.requireRole(roleId);
        AuditMetadata audit = auditMetadataFactory.current();
        List<Long> requestedIds = normalizeMenuIds(request == null ? null : request.menuIds());
        List<Long> menuIds = roleQueryService.retainActiveMenuIds(requestedIds);
        if (menuIds.isEmpty()) {
            throw new IllegalArgumentException("有效菜单不能为空（请求中的菜单均不存在或已删除）");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>()
                .eq(RoleMenuEntity::getRoleId, roleId));
        LocalDateTime now = audit.now();
        for (Long menuId : menuIds) {
            RoleMenuEntity entity = new RoleMenuEntity();
            entity.setRoleId(roleId);
            entity.setMenuId(menuId);
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            roleMenuMapper.insert(entity);
        }
        principalCache.evictAll();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return new RoleMenuAssignmentResponse(roleId, menuIds);
    }

    private RoleResponse updateStatus(Long id, String status) {
        RoleEntity entity = roleQueryService.requireRole(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(roleMapper.updateById(entity), "角色已被其他操作修改，请刷新后重试");
        principalCache.evictAll();
        userPermissionService.evictAccountBookPermissions(audit.companyId(), audit.accountBookId());
        return roleQueryService.toResponse(entity);
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
}

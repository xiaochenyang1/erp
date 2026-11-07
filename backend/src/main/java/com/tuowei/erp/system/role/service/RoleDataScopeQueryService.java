package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read side of role data scopes: read the assigned scope of a role plus the shared role guard and scope-row aggregation reused by the command service. */
@Service
public class RoleDataScopeQueryService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public RoleDataScopeQueryService(
            RoleMapper roleMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.roleMapper = roleMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public RoleDataScopeResponse getAssigned(Long roleId) {
        requireRole(roleId);
        List<RoleDataScopeEntity> scopes = roleDataScopeMapper.selectList(
                new LambdaQueryWrapper<RoleDataScopeEntity>()
                        .eq(RoleDataScopeEntity::getRoleId, roleId)
                        .orderByAsc(RoleDataScopeEntity::getId)
        );
        return toResponse(roleId, scopes);
    }

    RoleEntity requireRole(Long id) {
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

    RoleDataScopeResponse toResponse(Long roleId, List<RoleDataScopeEntity> scopes) {
        boolean hasAll = false;
        boolean dept = false;
        boolean post = false;
        boolean self = false;
        Set<Long> warehouseIds = new LinkedHashSet<>();
        for (RoleDataScopeEntity scope : scopes) {
            String type = scope.getScopeType() == null ? "" : scope.getScopeType().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case SCOPE_ALL -> hasAll = true;
                case SCOPE_DEPT -> dept = true;
                case SCOPE_POST -> post = true;
                case SCOPE_SELF -> self = true;
                case SCOPE_WAREHOUSE -> {
                    if (scope.getWarehouseId() != null) {
                        warehouseIds.add(scope.getWarehouseId());
                    }
                }
                default -> {
                    // ignore unknown historical rows
                }
            }
        }
        if (hasAll) {
            return new RoleDataScopeResponse(roleId, true, false, false, false, List.of());
        }
        return new RoleDataScopeResponse(
                roleId,
                false,
                dept,
                post,
                self,
                warehouseIds.stream().sorted().toList()
        );
    }
}

package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class RoleDataScopeService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;

    public RoleDataScopeService(
            RoleMapper roleMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache
    ) {
        this.roleMapper = roleMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
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

    @Transactional
    public RoleDataScopeResponse assign(Long roleId, RoleDataScopeAssignRequest request) {
        requireRole(roleId);
        AuditMetadata audit = auditMetadataFactory.current();
        RoleDataScopeAssignRequest safe = request == null
                ? new RoleDataScopeAssignRequest(false, false, false, false, List.of())
                : request;

        boolean hasAll = Boolean.TRUE.equals(safe.hasAllScope());
        boolean dept = Boolean.TRUE.equals(safe.deptScoped());
        boolean post = Boolean.TRUE.equals(safe.postScoped());
        boolean self = Boolean.TRUE.equals(safe.selfScoped());
        List<Long> warehouseIds = normalizeWarehouseIds(safe.warehouseIds());

        if (hasAll) {
            dept = false;
            post = false;
            self = false;
            warehouseIds = List.of();
        } else {
            for (Long warehouseId : warehouseIds) {
                requireActiveWarehouse(warehouseId, audit);
            }
        }

        roleDataScopeMapper.delete(new LambdaQueryWrapper<RoleDataScopeEntity>()
                .eq(RoleDataScopeEntity::getRoleId, roleId));

        LocalDateTime now = audit.now();
        List<RoleDataScopeEntity> inserted = new ArrayList<>();
        if (hasAll) {
            inserted.add(insertScope(roleId, SCOPE_ALL, null, audit.userId(), now));
        } else {
            if (dept) {
                inserted.add(insertScope(roleId, SCOPE_DEPT, null, audit.userId(), now));
            }
            if (post) {
                inserted.add(insertScope(roleId, SCOPE_POST, null, audit.userId(), now));
            }
            if (self) {
                inserted.add(insertScope(roleId, SCOPE_SELF, null, audit.userId(), now));
            }
            for (Long warehouseId : warehouseIds) {
                inserted.add(insertScope(roleId, SCOPE_WAREHOUSE, warehouseId, audit.userId(), now));
            }
        }

        // 角色范围影响所有持有该角色的用户，整表失效 principal 缓存
        principalCache.evictAll();
        return toResponse(roleId, inserted);
    }

    private RoleDataScopeEntity insertScope(
            Long roleId,
            String scopeType,
            Long warehouseId,
            Long createdBy,
            LocalDateTime createdTime
    ) {
        RoleDataScopeEntity entity = new RoleDataScopeEntity();
        entity.setRoleId(roleId);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        entity.setCreatedTime(createdTime);
        roleDataScopeMapper.insert(entity);
        return entity;
    }

    private RoleDataScopeResponse toResponse(Long roleId, List<RoleDataScopeEntity> scopes) {
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

    private List<Long> normalizeWarehouseIds(List<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(warehouseIds.stream()
                .filter(Objects::nonNull)
                .toList()));
    }

    private void requireActiveWarehouse(Long warehouseId, AuditMetadata audit) {
        WarehouseEntity warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null
                || warehouse.getDeletedFlag() == null
                || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), audit.companyId())
                || !Objects.equals(warehouse.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("仓库不存在或已停用: " + warehouseId);
        }
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
}

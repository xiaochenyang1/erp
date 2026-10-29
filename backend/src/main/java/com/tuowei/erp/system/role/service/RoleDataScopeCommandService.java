package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Write side of role data scopes: rewrite a role's assigned scopes with warehouse validation and full principal-cache eviction. */
@Service
public class RoleDataScopeCommandService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final RoleDataScopeMapper roleDataScopeMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final RoleDataScopeQueryService queryService;

    public RoleDataScopeCommandService(
            RoleDataScopeMapper roleDataScopeMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            RoleDataScopeQueryService queryService
    ) {
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.queryService = queryService;
    }

    @Transactional
    public RoleDataScopeResponse assign(Long roleId, RoleDataScopeAssignRequest request) {
        queryService.requireRole(roleId);
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
        return queryService.toResponse(roleId, inserted);
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
}

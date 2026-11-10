package com.tuowei.erp.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Write side of user data scopes: rewrite a user's assigned scopes with warehouse validation and per-user principal-cache eviction. */
@Service
public class UserDataScopeCommandService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final UserDataScopeMapper userDataScopeMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final UserDataScopeQueryService queryService;

    public UserDataScopeCommandService(
            UserDataScopeMapper userDataScopeMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            UserDataScopeQueryService queryService
    ) {
        this.userDataScopeMapper = userDataScopeMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.queryService = queryService;
    }

    @Transactional
    public UserDataScopeResponse assign(Long userId, UserDataScopeAssignRequest request) {
        UserEntity user = queryService.requireUser(userId);
        AuditMetadata audit = auditMetadataFactory.current();
        UserDataScopeAssignRequest safe = request == null
                ? new UserDataScopeAssignRequest(false, false, false, false, List.of())
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

        userDataScopeMapper.delete(new LambdaQueryWrapper<UserDataScopeEntity>()
                .eq(UserDataScopeEntity::getUserId, userId));

        LocalDateTime now = audit.now();
        List<UserDataScopeEntity> inserted = new ArrayList<>();
        if (hasAll) {
            inserted.add(insertScope(userId, SCOPE_ALL, null, audit.userId(), now));
        } else {
            if (dept) {
                inserted.add(insertScope(userId, SCOPE_DEPT, null, audit.userId(), now));
            }
            if (post) {
                inserted.add(insertScope(userId, SCOPE_POST, null, audit.userId(), now));
            }
            if (self) {
                inserted.add(insertScope(userId, SCOPE_SELF, null, audit.userId(), now));
            }
            for (Long warehouseId : warehouseIds) {
                inserted.add(insertScope(userId, SCOPE_WAREHOUSE, warehouseId, audit.userId(), now));
            }
        }

        principalCache.evictUser(userId);
        return queryService.toResponse(user, inserted);
    }

    private UserDataScopeEntity insertScope(
            Long userId,
            String scopeType,
            Long warehouseId,
            Long createdBy,
            LocalDateTime createdTime
    ) {
        UserDataScopeEntity entity = new UserDataScopeEntity();
        entity.setUserId(userId);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        entity.setCreatedTime(createdTime);
        userDataScopeMapper.insert(entity);
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

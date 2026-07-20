package com.tuowei.erp.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.web.UserDataScopeAssignRequest;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
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
public class UserDataScopeService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final UserMapper userMapper;
    private final UserDataScopeMapper userDataScopeMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SecurityPrincipalCache principalCache;
    private final DataScopeService dataScopeService;

    public UserDataScopeService(
            UserMapper userMapper,
            UserDataScopeMapper userDataScopeMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            SecurityPrincipalCache principalCache,
            DataScopeService dataScopeService
    ) {
        this.userMapper = userMapper;
        this.userDataScopeMapper = userDataScopeMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.principalCache = principalCache;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public UserDataScopeResponse getAssigned(Long userId) {
        UserEntity user = requireUser(userId);
        List<UserDataScopeEntity> scopes = userDataScopeMapper.selectList(
                new LambdaQueryWrapper<UserDataScopeEntity>()
                        .eq(UserDataScopeEntity::getUserId, userId)
                        .orderByAsc(UserDataScopeEntity::getId)
        );
        return toResponse(user, scopes);
    }

    @Transactional
    public UserDataScopeResponse assign(Long userId, UserDataScopeAssignRequest request) {
        UserEntity user = requireUser(userId);
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
        return toResponse(user, inserted);
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

    private UserDataScopeResponse toResponse(UserEntity user, List<UserDataScopeEntity> scopes) {
        ScopeFlags assigned = parseUserScopes(scopes);
        DataScopeSnapshot effective = dataScopeService.buildSnapshot(
                user.getId(),
                user.getCompanyId(),
                user.getAccountBookId()
        );
        if (assigned.hasAll) {
            assigned = ScopeFlags.all();
        }
        List<Long> effectiveWarehouses = effective.hasAllScope()
                ? List.of()
                : effective.warehouseIds().stream().sorted().toList();
        return new UserDataScopeResponse(
                user.getId(),
                assigned.hasAll,
                assigned.dept,
                assigned.post,
                assigned.self,
                assigned.warehouseIds,
                effective.hasAllScope(),
                effective.deptScoped(),
                effective.postScoped(),
                effective.selfScoped(),
                effectiveWarehouses
        );
    }

    private ScopeFlags parseUserScopes(List<UserDataScopeEntity> scopes) {
        boolean hasAll = false;
        boolean dept = false;
        boolean post = false;
        boolean self = false;
        Set<Long> warehouseIds = new LinkedHashSet<>();
        for (UserDataScopeEntity scope : scopes) {
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
            return ScopeFlags.all();
        }
        return new ScopeFlags(false, dept, post, self, warehouseIds.stream().sorted().toList());
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

    private UserEntity requireUser(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserEntity entity = userMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("用户不存在");
        }
        return entity;
    }

    private record ScopeFlags(
            boolean hasAll,
            boolean dept,
            boolean post,
            boolean self,
            List<Long> warehouseIds
    ) {
        static ScopeFlags all() {
            return new ScopeFlags(true, false, false, false, List.of());
        }
    }
}

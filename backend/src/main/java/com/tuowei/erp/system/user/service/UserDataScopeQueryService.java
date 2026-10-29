package com.tuowei.erp.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.web.UserDataScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read side of user data scopes: read a user's assigned scope (unioned with role scopes into the effective snapshot) plus the shared user guard and scope-row aggregation reused by the command service. */
@Service
public class UserDataScopeQueryService {

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_POST = "POST";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_WAREHOUSE = "WAREHOUSE";

    private final UserMapper userMapper;
    private final UserDataScopeMapper userDataScopeMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final DataScopeService dataScopeService;

    public UserDataScopeQueryService(
            UserMapper userMapper,
            UserDataScopeMapper userDataScopeMapper,
            AuditMetadataFactory auditMetadataFactory,
            DataScopeService dataScopeService
    ) {
        this.userMapper = userMapper;
        this.userDataScopeMapper = userDataScopeMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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

    UserEntity requireUser(Long id) {
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

    UserDataScopeResponse toResponse(UserEntity user, List<UserDataScopeEntity> scopes) {
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

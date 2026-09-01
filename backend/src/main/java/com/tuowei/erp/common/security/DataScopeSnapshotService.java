package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataScopeSnapshotService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final UserDataScopeMapper userDataScopeMapper;

    public DataScopeSnapshotService(
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            UserDataScopeMapper userDataScopeMapper
    ) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.userDataScopeMapper = userDataScopeMapper;
    }

    public DataScopeSnapshot buildSnapshot(Long userId, Long companyId, Long accountBookId) {
        List<RoleEntity> activeRoles = loadActiveRoles(userId, companyId, accountBookId);
        if (activeRoles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode()))) {
            return DataScopeSnapshot.all();
        }
        Set<Long> roleIds = activeRoles.stream()
                .map(RoleEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<RoleDataScopeEntity> roleScopes = roleIds.isEmpty()
                ? List.of()
                : roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeEntity>()
                .in(RoleDataScopeEntity::getRoleId, roleIds));
        List<UserDataScopeEntity> userScopes = userDataScopeMapper.selectList(
                new LambdaQueryWrapper<UserDataScopeEntity>()
                        .eq(UserDataScopeEntity::getUserId, userId)
        );

        DataScopeAccumulator accumulator = new DataScopeAccumulator();
        roleScopes.forEach(accumulator::add);
        userScopes.forEach(accumulator::add);
        return accumulator.snapshot();
    }

    private List<RoleEntity> loadActiveRoles(Long userId, Long companyId, Long accountBookId) {
        List<Long> assignedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        if (assignedRoleIds.isEmpty()) {
            return List.of();
        }

        return roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                .in(RoleEntity::getId, assignedRoleIds)
                .eq(RoleEntity::getCompanyId, companyId)
                .eq(RoleEntity::getAccountBookId, accountBookId)
                .eq(RoleEntity::getStatus, "ACTIVE")
                .eq(RoleEntity::getDeletedFlag, 0)
                .orderByAsc(RoleEntity::getId));
    }

    private static class DataScopeAccumulator {

        private boolean hasAll;
        private boolean dept;
        private boolean post;
        private boolean self;
        private final Set<Long> warehouseIds = new LinkedHashSet<>();

        private void add(RoleDataScopeEntity entity) {
            add(entity.getScopeType(), entity.getWarehouseId());
        }

        private void add(UserDataScopeEntity entity) {
            add(entity.getScopeType(), entity.getWarehouseId());
        }

        private void add(String scopeType, Long warehouseId) {
            DataScopeRule rule = DataScopeRule.from(scopeType);
            hasAll |= rule == DataScopeRule.ALL;
            dept |= rule == DataScopeRule.DEPT;
            post |= rule == DataScopeRule.POST;
            self |= rule == DataScopeRule.SELF;
            if (rule == DataScopeRule.WAREHOUSE && warehouseId != null) {
                warehouseIds.add(warehouseId);
            }
        }

        private DataScopeSnapshot snapshot() {
            if (hasAll) {
                return DataScopeSnapshot.all();
            }
            if (!dept && !post && !self && warehouseIds.isEmpty()) {
                return DataScopeSnapshot.none();
            }
            return new DataScopeSnapshot(false, dept, post, self, warehouseIds);
        }
    }
}

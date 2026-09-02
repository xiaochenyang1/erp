package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.security.access.AccessDeniedException;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class DataScopePolicySupport {

    private DataScopePolicySupport() {
    }

    static <T> LambdaQueryWrapper<T> applyCreatedByAndWarehouseScope(
            LambdaQueryWrapper<T> wrapper,
            Set<Long> visibleCreatorIds,
            Set<Long> warehouseIds,
            SFunction<T, Long> createdByColumn,
            SFunction<T, Long> warehouseIdColumn
    ) {
        if (visibleCreatorIds.isEmpty() && warehouseIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.in(warehouseIdColumn, warehouseIds);
        }
        if (warehouseIds.isEmpty()) {
            return wrapper.in(createdByColumn, visibleCreatorIds);
        }
        return wrapper.and(query -> query
                .in(createdByColumn, visibleCreatorIds)
                .or()
                .in(warehouseIdColumn, warehouseIds));
    }

    static <T> LambdaQueryWrapper<T> applyWarehouseScope(
            LambdaQueryWrapper<T> wrapper,
            DataScopeSnapshot snapshot,
            SFunction<T, Long> warehouseIdColumn
    ) {
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        if (snapshot.warehouseIds().isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(warehouseIdColumn, snapshot.warehouseIds());
    }

    static boolean canViewWarehouse(Long warehouseId, DataScopeSnapshot snapshot) {
        return snapshot.hasAllScope() || snapshot.warehouseIds().contains(warehouseId);
    }

    static boolean canViewByCreatorOrWarehouse(
            Long createdBy,
            Long warehouseId,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        if (snapshot.hasAllScope()) {
            return true;
        }
        if (snapshot.selfScoped() && Objects.equals(createdBy, currentUser.userId())) {
            return true;
        }
        if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
            return true;
        }
        if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
            return true;
        }
        return snapshot.warehouseIds().contains(warehouseId);
    }

    static Set<Long> visibleCreatorIds(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        Set<Long> visibleCreatorIds = new LinkedHashSet<>();
        if (snapshot.selfScoped()) {
            visibleCreatorIds.add(currentUser.userId());
        }
        if (snapshot.deptScoped()) {
            visibleCreatorIds.addAll(deptUserIds);
        }
        if (snapshot.postScoped()) {
            visibleCreatorIds.addAll(postUserIds);
        }
        return visibleCreatorIds;
    }

    static void assertSameTenant(
            Long entityCompanyId,
            Long entityAccountBookId,
            CurrentUser currentUser,
            String message
    ) {
        if (!Objects.equals(entityCompanyId, currentUser.companyId())
                || !Objects.equals(entityAccountBookId, currentUser.accountBookId())) {
            throw new AccessDeniedException(message);
        }
    }
}

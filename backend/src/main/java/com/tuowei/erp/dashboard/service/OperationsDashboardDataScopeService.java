package com.tuowei.erp.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class OperationsDashboardDataScopeService {

    private final DataScopeService dataScopeService;

    public OperationsDashboardDataScopeService(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    public LambdaQueryWrapper<PurchaseOrderEntity> applyPurchaseOrderScope(
            LambdaQueryWrapper<PurchaseOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applyPurchaseOrderScope(
                wrapper, currentUser, snapshot, scopedUserIds.deptUserIds(), scopedUserIds.postUserIds());
    }

    public LambdaQueryWrapper<SalesOrderEntity> applySalesOrderScope(
            LambdaQueryWrapper<SalesOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        return dataScopeService.applySalesOrderScope(
                wrapper, currentUser, snapshot, scopedUserIds.deptUserIds(), scopedUserIds.postUserIds());
    }

    public AggregationScope resolveAggregationScope(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            ScopedUserResolver.ScopedUserIds scopedUserIds
    ) {
        if (snapshot.hasAllScope()) {
            return new AggregationScope(null, null);
        }

        Set<Long> visibleCreatorIds = new LinkedHashSet<>();
        if (snapshot.selfScoped()) {
            visibleCreatorIds.add(currentUser.userId());
        }
        if (snapshot.deptScoped()) {
            visibleCreatorIds.addAll(scopedUserIds.deptUserIds());
        }
        if (snapshot.postScoped()) {
            visibleCreatorIds.addAll(scopedUserIds.postUserIds());
        }
        return new AggregationScope(visibleCreatorIds, snapshot.warehouseIds());
    }

    public record AggregationScope(Set<Long> visibleCreatorIds, Set<Long> warehouseIds) {

        public AggregationScope {
            visibleCreatorIds = visibleCreatorIds == null ? null : Set.copyOf(visibleCreatorIds);
            warehouseIds = warehouseIds == null ? null : Set.copyOf(warehouseIds);
        }
    }
}

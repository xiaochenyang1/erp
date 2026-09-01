package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.tuowei.erp.common.security.DataScopePolicySupport.assertSameTenant;
import static com.tuowei.erp.common.security.DataScopePolicySupport.visibleCreatorIds;

@Service
public class ProductionDataScopeService {

    public LambdaQueryWrapper<ProductionOrderEntity> applyProductionOrderScope(
            LambdaQueryWrapper<ProductionOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        if (snapshot.hasAllScope()) {
            return wrapper;
        }

        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        Set<Long> warehouseIds = snapshot.warehouseIds();
        if (visibleCreatorIds.isEmpty() && warehouseIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.in(ProductionOrderEntity::getMaterialWarehouseId, warehouseIds)
                    .in(ProductionOrderEntity::getFinishedWarehouseId, warehouseIds);
        }
        if (warehouseIds.isEmpty()) {
            return wrapper.in(ProductionOrderEntity::getCreatedBy, visibleCreatorIds);
        }
        return wrapper.and(query -> query
                .in(ProductionOrderEntity::getCreatedBy, visibleCreatorIds)
                .or(scope -> scope
                        .in(ProductionOrderEntity::getMaterialWarehouseId, warehouseIds)
                        .in(ProductionOrderEntity::getFinishedWarehouseId, warehouseIds)));
    }

    public void assertCanViewProductionOrder(
            ProductionOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该生产工单");
        if (snapshot.hasAllScope()) {
            return;
        }
        if (snapshot.selfScoped() && Objects.equals(entity.getCreatedBy(), currentUser.userId())) {
            return;
        }
        if (snapshot.deptScoped() && Objects.equals(creatorDeptId, currentUser.deptId())) {
            return;
        }
        if (snapshot.postScoped() && Objects.equals(creatorPostId, currentUser.postId())) {
            return;
        }
        if (snapshot.warehouseIds().contains(entity.getMaterialWarehouseId())
                && snapshot.warehouseIds().contains(entity.getFinishedWarehouseId())) {
            return;
        }
        throw new AccessDeniedException("无权访问该生产工单");
    }
}

package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.tuowei.erp.common.security.DataScopePolicySupport.applyCreatedByAndWarehouseScope;
import static com.tuowei.erp.common.security.DataScopePolicySupport.applyWarehouseScope;
import static com.tuowei.erp.common.security.DataScopePolicySupport.assertSameTenant;
import static com.tuowei.erp.common.security.DataScopePolicySupport.canViewByCreatorOrWarehouse;
import static com.tuowei.erp.common.security.DataScopePolicySupport.canViewWarehouse;
import static com.tuowei.erp.common.security.DataScopePolicySupport.visibleCreatorIds;

@Service
public class InventoryDataScopeService {

    public LambdaQueryWrapper<InventoryBalanceEntity> applyInventoryBalanceScope(
            LambdaQueryWrapper<InventoryBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return applyWarehouseScope(wrapper, snapshot, InventoryBalanceEntity::getWarehouseId);
    }

    public void assertCanViewInventoryBalance(InventoryBalanceEntity entity, DataScopeSnapshot snapshot) {
        if (canViewWarehouse(entity.getWarehouseId(), snapshot)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存余额");
    }

    public LambdaQueryWrapper<InventoryLotBalanceEntity> applyInventoryLotBalanceScope(
            LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return applyWarehouseScope(wrapper, snapshot, InventoryLotBalanceEntity::getWarehouseId);
    }

    public void assertCanViewInventoryLotBalance(InventoryLotBalanceEntity entity, DataScopeSnapshot snapshot) {
        if (canViewWarehouse(entity.getWarehouseId(), snapshot)) {
            return;
        }
        throw new AccessDeniedException("无权访问该批次库存余额");
    }

    public LambdaQueryWrapper<InventoryTransactionEntity> applyInventoryTransactionScope(
            LambdaQueryWrapper<InventoryTransactionEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return applyWarehouseScope(wrapper, snapshot, InventoryTransactionEntity::getWarehouseId);
    }

    public void assertCanViewInventoryTransaction(InventoryTransactionEntity entity, DataScopeSnapshot snapshot) {
        if (canViewWarehouse(entity.getWarehouseId(), snapshot)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存流水");
    }

    public LambdaQueryWrapper<InventoryReservationEntity> applyInventoryReservationScope(
            LambdaQueryWrapper<InventoryReservationEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return applyWarehouseScope(wrapper, snapshot, InventoryReservationEntity::getWarehouseId);
    }

    public void assertCanViewInventoryReservation(InventoryReservationEntity entity, DataScopeSnapshot snapshot) {
        if (canViewWarehouse(entity.getWarehouseId(), snapshot)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存预占");
    }

    public void assertCanViewInventoryTransfer(
            InventoryTransferEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该库存调拨单");
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
        if (snapshot.warehouseIds().contains(entity.getFromWarehouseId())
                && snapshot.warehouseIds().contains(entity.getToWarehouseId())) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存调拨单");
    }

    public LambdaQueryWrapper<InventoryTransferEntity> applyInventoryTransferScope(
            LambdaQueryWrapper<InventoryTransferEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(InventoryTransferEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryTransferEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }

        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        if (visibleCreatorIds.isEmpty() && snapshot.warehouseIds().isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.and(query -> query
                    .in(InventoryTransferEntity::getFromWarehouseId, snapshot.warehouseIds())
                    .in(InventoryTransferEntity::getToWarehouseId, snapshot.warehouseIds()));
        }
        if (snapshot.warehouseIds().isEmpty()) {
            return wrapper.in(InventoryTransferEntity::getCreatedBy, visibleCreatorIds);
        }
        return wrapper.and(query -> query
                .in(InventoryTransferEntity::getCreatedBy, visibleCreatorIds)
                .or(nested -> nested
                        .in(InventoryTransferEntity::getFromWarehouseId, snapshot.warehouseIds())
                        .in(InventoryTransferEntity::getToWarehouseId, snapshot.warehouseIds())));
    }

    public LambdaQueryWrapper<InventoryAdjustmentEntity> applyInventoryAdjustmentScope(
            LambdaQueryWrapper<InventoryAdjustmentEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(InventoryAdjustmentEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryAdjustmentEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
                snapshot.warehouseIds(),
                InventoryAdjustmentEntity::getCreatedBy,
                InventoryAdjustmentEntity::getWarehouseId
        );
    }

    public void assertCanViewInventoryAdjustment(
            InventoryAdjustmentEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该库存调整单");
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存调整单");
    }

    public LambdaQueryWrapper<InventoryStockCheckEntity> applyInventoryStockCheckScope(
            LambdaQueryWrapper<InventoryStockCheckEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(InventoryStockCheckEntity::getCompanyId, currentUser.companyId())
                .eq(InventoryStockCheckEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
                snapshot.warehouseIds(),
                InventoryStockCheckEntity::getCreatedBy,
                InventoryStockCheckEntity::getWarehouseId
        );
    }

    public void assertCanViewInventoryStockCheck(
            InventoryStockCheckEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该库存盘点单");
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存盘点单");
    }
}

package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.tuowei.erp.common.security.DataScopePolicySupport.applyCreatedByAndWarehouseScope;
import static com.tuowei.erp.common.security.DataScopePolicySupport.assertSameTenant;
import static com.tuowei.erp.common.security.DataScopePolicySupport.canViewByCreatorOrWarehouse;
import static com.tuowei.erp.common.security.DataScopePolicySupport.visibleCreatorIds;

@Service
public class DataScopeService {

    private final DataScopeSnapshotService snapshotService;
    private final InventoryDataScopeService inventoryDataScopeService;

    @Autowired
    public DataScopeService(
            DataScopeSnapshotService snapshotService,
            InventoryDataScopeService inventoryDataScopeService
    ) {
        this.snapshotService = snapshotService;
        this.inventoryDataScopeService = inventoryDataScopeService;
    }

    /** Keeps direct construction introduced with the snapshot split compatible. */
    public DataScopeService(DataScopeSnapshotService snapshotService) {
        this(snapshotService, new InventoryDataScopeService());
    }

    /** Keeps direct construction in existing non-Spring tests and integrations compatible. */
    public DataScopeService(
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            UserDataScopeMapper userDataScopeMapper
    ) {
        this(new DataScopeSnapshotService(
                userRoleMapper,
                roleMapper,
                roleDataScopeMapper,
                userDataScopeMapper
        ));
    }

    public DataScopeSnapshot buildSnapshot(Long userId, Long companyId, Long accountBookId) {
        return snapshotService.buildSnapshot(userId, companyId, accountBookId);
    }

    public LambdaQueryWrapper<PurchaseOrderEntity> applyPurchaseOrderScope(
            LambdaQueryWrapper<PurchaseOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(PurchaseOrderEntity::getCompanyId, currentUser.companyId())
                .eq(PurchaseOrderEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }

        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(PurchaseOrderEntity::getCreatedBy, visibleCreatorIds);
    }

    public void assertCanViewPurchaseOrder(
            PurchaseOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该采购订单");
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
        throw new AccessDeniedException("无权访问该采购订单");
    }

    public LambdaQueryWrapper<SalesOrderEntity> applySalesOrderScope(
            LambdaQueryWrapper<SalesOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(SalesOrderEntity::getCompanyId, currentUser.companyId())
                .eq(SalesOrderEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }

        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(SalesOrderEntity::getCreatedBy, visibleCreatorIds);
    }

    public void assertCanViewSalesOrder(
            SalesOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该销售订单");
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
        throw new AccessDeniedException("无权访问该销售订单");
    }

    public LambdaQueryWrapper<SalesDeliveryEntity> applySalesDeliveryScope(
            LambdaQueryWrapper<SalesDeliveryEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(SalesDeliveryEntity::getCompanyId, currentUser.companyId())
                .eq(SalesDeliveryEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds,
                snapshot.warehouseIds(),
                SalesDeliveryEntity::getCreatedBy,
                SalesDeliveryEntity::getWarehouseId
        );
    }

    public void assertCanViewSalesDelivery(
            SalesDeliveryEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该销售出库单");
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该销售出库单");
    }

    public LambdaQueryWrapper<SalesReturnEntity> applySalesReturnScope(
            LambdaQueryWrapper<SalesReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(SalesReturnEntity::getCompanyId, currentUser.companyId())
                .eq(SalesReturnEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds,
                snapshot.warehouseIds(),
                SalesReturnEntity::getCreatedBy,
                SalesReturnEntity::getWarehouseId
        );
    }

    public void assertCanViewSalesReturn(
            SalesReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该销售退货单");
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该销售退货单");
    }

    public LambdaQueryWrapper<PurchaseReceiptEntity> applyPurchaseReceiptScope(
            LambdaQueryWrapper<PurchaseReceiptEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(PurchaseReceiptEntity::getCompanyId, currentUser.companyId())
                .eq(PurchaseReceiptEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds,
                snapshot.warehouseIds(),
                PurchaseReceiptEntity::getCreatedBy,
                PurchaseReceiptEntity::getWarehouseId
        );
    }

    public void assertCanViewPurchaseReceipt(
            PurchaseReceiptEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该采购入库单");
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该采购入库单");
    }

    public LambdaQueryWrapper<PurchaseReturnEntity> applyPurchaseReturnScope(
            LambdaQueryWrapper<PurchaseReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        wrapper.eq(PurchaseReturnEntity::getCompanyId, currentUser.companyId())
                .eq(PurchaseReturnEntity::getAccountBookId, currentUser.accountBookId());
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds,
                snapshot.warehouseIds(),
                PurchaseReturnEntity::getCreatedBy,
                PurchaseReturnEntity::getWarehouseId
        );
    }

    public void assertCanViewPurchaseReturn(
            PurchaseReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        assertSameTenant(entity.getCompanyId(), entity.getAccountBookId(), currentUser, "无权访问该采购退货单");
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该采购退货单");
    }

    public LambdaQueryWrapper<InventoryBalanceEntity> applyInventoryBalanceScope(
            LambdaQueryWrapper<InventoryBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return inventoryDataScopeService.applyInventoryBalanceScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryBalance(InventoryBalanceEntity entity, DataScopeSnapshot snapshot) {
        inventoryDataScopeService.assertCanViewInventoryBalance(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryLotBalanceEntity> applyInventoryLotBalanceScope(
            LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return inventoryDataScopeService.applyInventoryLotBalanceScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryLotBalance(InventoryLotBalanceEntity entity, DataScopeSnapshot snapshot) {
        inventoryDataScopeService.assertCanViewInventoryLotBalance(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryTransactionEntity> applyInventoryTransactionScope(
            LambdaQueryWrapper<InventoryTransactionEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return inventoryDataScopeService.applyInventoryTransactionScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryTransaction(InventoryTransactionEntity entity, DataScopeSnapshot snapshot) {
        inventoryDataScopeService.assertCanViewInventoryTransaction(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryReservationEntity> applyInventoryReservationScope(
            LambdaQueryWrapper<InventoryReservationEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return inventoryDataScopeService.applyInventoryReservationScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryReservation(InventoryReservationEntity entity, DataScopeSnapshot snapshot) {
        inventoryDataScopeService.assertCanViewInventoryReservation(entity, snapshot);
    }

    public void assertCanViewInventoryTransfer(
            InventoryTransferEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        inventoryDataScopeService.assertCanViewInventoryTransfer(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<InventoryTransferEntity> applyInventoryTransferScope(
            LambdaQueryWrapper<InventoryTransferEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return inventoryDataScopeService.applyInventoryTransferScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public LambdaQueryWrapper<InventoryAdjustmentEntity> applyInventoryAdjustmentScope(
            LambdaQueryWrapper<InventoryAdjustmentEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return inventoryDataScopeService.applyInventoryAdjustmentScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewInventoryAdjustment(
            InventoryAdjustmentEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        inventoryDataScopeService.assertCanViewInventoryAdjustment(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<InventoryStockCheckEntity> applyInventoryStockCheckScope(
            LambdaQueryWrapper<InventoryStockCheckEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return inventoryDataScopeService.applyInventoryStockCheckScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewInventoryStockCheck(
            InventoryStockCheckEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        inventoryDataScopeService.assertCanViewInventoryStockCheck(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
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

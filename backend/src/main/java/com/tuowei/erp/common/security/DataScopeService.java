package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 数据范围兼容门面：仅依赖快照解析与策略两个协作者，逐一委托原公开 API。
 * 快照解析（读 Mapper）→ {@link DataScopeSnapshotService}；范围应用与查看鉴权（纯函数）→ {@link DataScopePolicyService}。
 */
@Service
public class DataScopeService {

    private final DataScopeSnapshotService snapshotService;
    private final DataScopePolicyService policyService;

    public DataScopeService(DataScopeSnapshotService snapshotService, DataScopePolicyService policyService) {
        this.snapshotService = snapshotService;
        this.policyService = policyService;
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
        return policyService.applyPurchaseOrderScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseOrder(
            PurchaseOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewPurchaseOrder(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesOrderEntity> applySalesOrderScope(
            LambdaQueryWrapper<SalesOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applySalesOrderScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesOrder(
            SalesOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewSalesOrder(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesDeliveryEntity> applySalesDeliveryScope(
            LambdaQueryWrapper<SalesDeliveryEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applySalesDeliveryScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesDelivery(
            SalesDeliveryEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewSalesDelivery(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesReturnEntity> applySalesReturnScope(
            LambdaQueryWrapper<SalesReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applySalesReturnScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesReturn(
            SalesReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewSalesReturn(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<PurchaseReceiptEntity> applyPurchaseReceiptScope(
            LambdaQueryWrapper<PurchaseReceiptEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applyPurchaseReceiptScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseReceipt(
            PurchaseReceiptEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewPurchaseReceipt(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<PurchaseReturnEntity> applyPurchaseReturnScope(
            LambdaQueryWrapper<PurchaseReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applyPurchaseReturnScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseReturn(
            PurchaseReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewPurchaseReturn(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<InventoryBalanceEntity> applyInventoryBalanceScope(
            LambdaQueryWrapper<InventoryBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return policyService.applyInventoryBalanceScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryBalance(InventoryBalanceEntity entity, DataScopeSnapshot snapshot) {
        policyService.assertCanViewInventoryBalance(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryLotBalanceEntity> applyInventoryLotBalanceScope(
            LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return policyService.applyInventoryLotBalanceScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryLotBalance(InventoryLotBalanceEntity entity, DataScopeSnapshot snapshot) {
        policyService.assertCanViewInventoryLotBalance(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryTransactionEntity> applyInventoryTransactionScope(
            LambdaQueryWrapper<InventoryTransactionEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return policyService.applyInventoryTransactionScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryTransaction(InventoryTransactionEntity entity, DataScopeSnapshot snapshot) {
        policyService.assertCanViewInventoryTransaction(entity, snapshot);
    }

    public LambdaQueryWrapper<InventoryReservationEntity> applyInventoryReservationScope(
            LambdaQueryWrapper<InventoryReservationEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return policyService.applyInventoryReservationScope(wrapper, snapshot);
    }

    public void assertCanViewInventoryReservation(InventoryReservationEntity entity, DataScopeSnapshot snapshot) {
        policyService.assertCanViewInventoryReservation(entity, snapshot);
    }

    public void assertCanViewInventoryTransfer(
            InventoryTransferEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewInventoryTransfer(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<InventoryTransferEntity> applyInventoryTransferScope(
            LambdaQueryWrapper<InventoryTransferEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applyInventoryTransferScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public LambdaQueryWrapper<InventoryAdjustmentEntity> applyInventoryAdjustmentScope(
            LambdaQueryWrapper<InventoryAdjustmentEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applyInventoryAdjustmentScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewInventoryAdjustment(
            InventoryAdjustmentEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewInventoryAdjustment(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<InventoryStockCheckEntity> applyInventoryStockCheckScope(
            LambdaQueryWrapper<InventoryStockCheckEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return policyService.applyInventoryStockCheckScope(wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewInventoryStockCheck(
            InventoryStockCheckEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewInventoryStockCheck(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public void assertCanViewProductionOrder(
            ProductionOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        policyService.assertCanViewProductionOrder(entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }
}

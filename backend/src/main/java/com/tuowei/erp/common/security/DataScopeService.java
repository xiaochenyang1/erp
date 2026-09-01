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
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class DataScopeService {

    private final DataScopeSnapshotService snapshotService;
    private final InventoryDataScopeService inventoryDataScopeService;
    private final SalesPurchaseDataScopeService salesPurchaseDataScopeService;
    private final ProductionDataScopeService productionDataScopeService;

    @Autowired
    DataScopeService(
            DataScopeSnapshotService snapshotService,
            InventoryDataScopeService inventoryDataScopeService,
            SalesPurchaseDataScopeService salesPurchaseDataScopeService,
            ProductionDataScopeService productionDataScopeService
    ) {
        this.snapshotService = snapshotService;
        this.inventoryDataScopeService = inventoryDataScopeService;
        this.salesPurchaseDataScopeService = salesPurchaseDataScopeService;
        this.productionDataScopeService = productionDataScopeService;
    }

    /** Keeps direct construction introduced with the sales and purchase policy split compatible. */
    public DataScopeService(
            DataScopeSnapshotService snapshotService,
            InventoryDataScopeService inventoryDataScopeService,
            SalesPurchaseDataScopeService salesPurchaseDataScopeService
    ) {
        this(snapshotService, inventoryDataScopeService, salesPurchaseDataScopeService,
                new ProductionDataScopeService());
    }

    /** Keeps direct construction introduced with the inventory policy split compatible. */
    public DataScopeService(
            DataScopeSnapshotService snapshotService,
            InventoryDataScopeService inventoryDataScopeService
    ) {
        this(snapshotService, inventoryDataScopeService, new SalesPurchaseDataScopeService(),
                new ProductionDataScopeService());
    }

    /** Keeps direct construction introduced with the snapshot split compatible. */
    public DataScopeService(DataScopeSnapshotService snapshotService) {
        this(snapshotService, new InventoryDataScopeService(), new SalesPurchaseDataScopeService(),
                new ProductionDataScopeService());
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
        return salesPurchaseDataScopeService.applyPurchaseOrderScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseOrder(
            PurchaseOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewPurchaseOrder(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesOrderEntity> applySalesOrderScope(
            LambdaQueryWrapper<SalesOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return salesPurchaseDataScopeService.applySalesOrderScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesOrder(
            SalesOrderEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewSalesOrder(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesDeliveryEntity> applySalesDeliveryScope(
            LambdaQueryWrapper<SalesDeliveryEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return salesPurchaseDataScopeService.applySalesDeliveryScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesDelivery(
            SalesDeliveryEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewSalesDelivery(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<SalesReturnEntity> applySalesReturnScope(
            LambdaQueryWrapper<SalesReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return salesPurchaseDataScopeService.applySalesReturnScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewSalesReturn(
            SalesReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewSalesReturn(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<PurchaseReceiptEntity> applyPurchaseReceiptScope(
            LambdaQueryWrapper<PurchaseReceiptEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return salesPurchaseDataScopeService.applyPurchaseReceiptScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseReceipt(
            PurchaseReceiptEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewPurchaseReceipt(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<PurchaseReturnEntity> applyPurchaseReturnScope(
            LambdaQueryWrapper<PurchaseReturnEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return salesPurchaseDataScopeService.applyPurchaseReturnScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

    public void assertCanViewPurchaseReturn(
            PurchaseReturnEntity entity,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Long creatorDeptId,
            Long creatorPostId
    ) {
        salesPurchaseDataScopeService.assertCanViewPurchaseReturn(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
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
        productionDataScopeService.assertCanViewProductionOrder(
                entity, currentUser, snapshot, creatorDeptId, creatorPostId);
    }

    public LambdaQueryWrapper<ProductionOrderEntity> applyProductionOrderScope(
            LambdaQueryWrapper<ProductionOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        return productionDataScopeService.applyProductionOrderScope(
                wrapper, currentUser, snapshot, deptUserIds, postUserIds);
    }

}

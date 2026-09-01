package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

import static com.tuowei.erp.common.security.DataScopePolicySupport.applyCreatedByAndWarehouseScope;
import static com.tuowei.erp.common.security.DataScopePolicySupport.assertSameTenant;
import static com.tuowei.erp.common.security.DataScopePolicySupport.canViewByCreatorOrWarehouse;
import static com.tuowei.erp.common.security.DataScopePolicySupport.visibleCreatorIds;

@Service
public class SalesPurchaseDataScopeService {

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
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
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
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
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
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
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
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
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
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
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
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
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
        return applyCreatedByAndWarehouseScope(
                wrapper,
                visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds),
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
        if (canViewByCreatorOrWarehouse(
                entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该采购退货单");
    }
}

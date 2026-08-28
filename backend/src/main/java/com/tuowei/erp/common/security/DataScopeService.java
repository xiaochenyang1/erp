package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.datascope.model.UserDataScopeEntity;
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
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataScopeService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final UserDataScopeMapper userDataScopeMapper;

    public DataScopeService(
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
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
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
        if (canViewByCreatorOrWarehouse(entity.getCreatedBy(), entity.getWarehouseId(), currentUser, snapshot, creatorDeptId, creatorPostId)) {
            return;
        }
        throw new AccessDeniedException("无权访问该库存盘点单");
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

    private <T> LambdaQueryWrapper<T> applyCreatedByAndWarehouseScope(
            LambdaQueryWrapper<T> wrapper,
            Set<Long> visibleCreatorIds,
            Set<Long> warehouseIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> createdByColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> warehouseIdColumn
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

    private <T> LambdaQueryWrapper<T> applyWarehouseScope(
            LambdaQueryWrapper<T> wrapper,
            DataScopeSnapshot snapshot,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> warehouseIdColumn
    ) {
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        if (snapshot.warehouseIds().isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        return wrapper.in(warehouseIdColumn, snapshot.warehouseIds());
    }

    private boolean canViewWarehouse(Long warehouseId, DataScopeSnapshot snapshot) {
        return snapshot.hasAllScope() || snapshot.warehouseIds().contains(warehouseId);
    }

    private boolean canViewByCreatorOrWarehouse(
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

    private Set<Long> visibleCreatorIds(
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

    private void assertSameTenant(Long entityCompanyId, Long entityAccountBookId, CurrentUser currentUser, String message) {
        if (!Objects.equals(entityCompanyId, currentUser.companyId())
                || !Objects.equals(entityAccountBookId, currentUser.accountBookId())) {
            throw new AccessDeniedException(message);
        }
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

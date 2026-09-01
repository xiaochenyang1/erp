package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataScopeServiceDecompositionTest {

    @Test
    void policyFacadeDependsOnDomainServicesWhileSnapshotServiceOwnsPersistence() {
        assertThat(autowiredConstructorDependencies(DataScopeService.class))
                .containsExactlyInAnyOrder(
                        DataScopeSnapshotService.class,
                        InventoryDataScopeService.class,
                        SalesPurchaseDataScopeService.class
                );
        assertThat(constructorDependencies(DataScopeSnapshotService.class))
                .containsExactlyInAnyOrder(
                        UserRoleMapper.class,
                        RoleMapper.class,
                        RoleDataScopeMapper.class,
                        UserDataScopeMapper.class
                )
                .doesNotContain(DataScopeService.class);
        assertThat(constructorDependencies(InventoryDataScopeService.class)).isEmpty();
        assertThat(constructorDependencies(SalesPurchaseDataScopeService.class)).isEmpty();
    }

    @Test
    void facadeDelegatesSnapshotConstruction() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        InventoryDataScopeService inventoryDataScopeService = mock(InventoryDataScopeService.class);
        SalesPurchaseDataScopeService salesPurchaseDataScopeService = mock(SalesPurchaseDataScopeService.class);
        DataScopeSnapshot expected = DataScopeSnapshot.all();
        when(snapshotService.buildSnapshot(7L, 11L, 13L)).thenReturn(expected);

        DataScopeSnapshot actual = new DataScopeService(
                snapshotService, inventoryDataScopeService, salesPurchaseDataScopeService
        ).buildSnapshot(7L, 11L, 13L);

        assertThat(actual).isSameAs(expected);
        verify(snapshotService).buildSnapshot(7L, 11L, 13L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesAllInventoryPolicyApis() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        InventoryDataScopeService inventoryService = mock(InventoryDataScopeService.class);
        SalesPurchaseDataScopeService salesPurchaseService = mock(SalesPurchaseDataScopeService.class);
        DataScopeService facade = new DataScopeService(snapshotService, inventoryService, salesPurchaseService);
        CurrentUser currentUser = new CurrentUser(7L, 11L, 13L, 17L, 19L, "scope", "Scope");
        DataScopeSnapshot snapshot = DataScopeSnapshot.none();
        Set<Long> deptUserIds = Set.of(23L);
        Set<Long> postUserIds = Set.of(29L);

        LambdaQueryWrapper<InventoryBalanceEntity> balanceWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryLotBalanceEntity> lotBalanceWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryTransactionEntity> transactionWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryReservationEntity> reservationWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryTransferEntity> transferWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryAdjustmentEntity> adjustmentWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<InventoryStockCheckEntity> stockCheckWrapper = mock(LambdaQueryWrapper.class);
        InventoryBalanceEntity balance = mock(InventoryBalanceEntity.class);
        InventoryLotBalanceEntity lotBalance = mock(InventoryLotBalanceEntity.class);
        InventoryTransactionEntity transaction = mock(InventoryTransactionEntity.class);
        InventoryReservationEntity reservation = mock(InventoryReservationEntity.class);
        InventoryTransferEntity transfer = mock(InventoryTransferEntity.class);
        InventoryAdjustmentEntity adjustment = mock(InventoryAdjustmentEntity.class);
        InventoryStockCheckEntity stockCheck = mock(InventoryStockCheckEntity.class);
        when(inventoryService.applyInventoryBalanceScope(balanceWrapper, snapshot)).thenReturn(balanceWrapper);
        when(inventoryService.applyInventoryLotBalanceScope(lotBalanceWrapper, snapshot)).thenReturn(lotBalanceWrapper);
        when(inventoryService.applyInventoryTransactionScope(transactionWrapper, snapshot)).thenReturn(transactionWrapper);
        when(inventoryService.applyInventoryReservationScope(reservationWrapper, snapshot)).thenReturn(reservationWrapper);
        when(inventoryService.applyInventoryTransferScope(
                transferWrapper, currentUser, snapshot, deptUserIds, postUserIds)).thenReturn(transferWrapper);
        when(inventoryService.applyInventoryAdjustmentScope(
                adjustmentWrapper, currentUser, snapshot, deptUserIds, postUserIds)).thenReturn(adjustmentWrapper);
        when(inventoryService.applyInventoryStockCheckScope(
                stockCheckWrapper, currentUser, snapshot, deptUserIds, postUserIds)).thenReturn(stockCheckWrapper);

        assertThat(facade.applyInventoryBalanceScope(balanceWrapper, snapshot)).isSameAs(balanceWrapper);
        facade.assertCanViewInventoryBalance(balance, snapshot);
        assertThat(facade.applyInventoryLotBalanceScope(lotBalanceWrapper, snapshot)).isSameAs(lotBalanceWrapper);
        facade.assertCanViewInventoryLotBalance(lotBalance, snapshot);
        assertThat(facade.applyInventoryTransactionScope(transactionWrapper, snapshot)).isSameAs(transactionWrapper);
        facade.assertCanViewInventoryTransaction(transaction, snapshot);
        assertThat(facade.applyInventoryReservationScope(reservationWrapper, snapshot)).isSameAs(reservationWrapper);
        facade.assertCanViewInventoryReservation(reservation, snapshot);
        assertThat(facade.applyInventoryTransferScope(
                transferWrapper, currentUser, snapshot, deptUserIds, postUserIds)).isSameAs(transferWrapper);
        facade.assertCanViewInventoryTransfer(transfer, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applyInventoryAdjustmentScope(
                adjustmentWrapper, currentUser, snapshot, deptUserIds, postUserIds)).isSameAs(adjustmentWrapper);
        facade.assertCanViewInventoryAdjustment(adjustment, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applyInventoryStockCheckScope(
                stockCheckWrapper, currentUser, snapshot, deptUserIds, postUserIds)).isSameAs(stockCheckWrapper);
        facade.assertCanViewInventoryStockCheck(stockCheck, currentUser, snapshot, 31L, 37L);

        verify(inventoryService).applyInventoryBalanceScope(balanceWrapper, snapshot);
        verify(inventoryService).assertCanViewInventoryBalance(balance, snapshot);
        verify(inventoryService).applyInventoryLotBalanceScope(lotBalanceWrapper, snapshot);
        verify(inventoryService).assertCanViewInventoryLotBalance(lotBalance, snapshot);
        verify(inventoryService).applyInventoryTransactionScope(transactionWrapper, snapshot);
        verify(inventoryService).assertCanViewInventoryTransaction(transaction, snapshot);
        verify(inventoryService).applyInventoryReservationScope(reservationWrapper, snapshot);
        verify(inventoryService).assertCanViewInventoryReservation(reservation, snapshot);
        verify(inventoryService).applyInventoryTransferScope(
                transferWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(inventoryService).assertCanViewInventoryTransfer(transfer, currentUser, snapshot, 31L, 37L);
        verify(inventoryService).applyInventoryAdjustmentScope(
                adjustmentWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(inventoryService).assertCanViewInventoryAdjustment(adjustment, currentUser, snapshot, 31L, 37L);
        verify(inventoryService).applyInventoryStockCheckScope(
                stockCheckWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(inventoryService).assertCanViewInventoryStockCheck(stockCheck, currentUser, snapshot, 31L, 37L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesAllSalesPurchasePolicyApis() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        InventoryDataScopeService inventoryService = mock(InventoryDataScopeService.class);
        SalesPurchaseDataScopeService salesPurchaseService = mock(SalesPurchaseDataScopeService.class);
        DataScopeService facade = new DataScopeService(snapshotService, inventoryService, salesPurchaseService);
        CurrentUser currentUser = new CurrentUser(7L, 11L, 13L, 17L, 19L, "scope", "Scope");
        DataScopeSnapshot snapshot = DataScopeSnapshot.none();
        Set<Long> deptUserIds = Set.of(23L);
        Set<Long> postUserIds = Set.of(29L);

        LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrderWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<SalesOrderEntity> salesOrderWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<SalesDeliveryEntity> salesDeliveryWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<SalesReturnEntity> salesReturnWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<PurchaseReceiptEntity> purchaseReceiptWrapper = mock(LambdaQueryWrapper.class);
        LambdaQueryWrapper<PurchaseReturnEntity> purchaseReturnWrapper = mock(LambdaQueryWrapper.class);
        PurchaseOrderEntity purchaseOrder = mock(PurchaseOrderEntity.class);
        SalesOrderEntity salesOrder = mock(SalesOrderEntity.class);
        SalesDeliveryEntity salesDelivery = mock(SalesDeliveryEntity.class);
        SalesReturnEntity salesReturn = mock(SalesReturnEntity.class);
        PurchaseReceiptEntity purchaseReceipt = mock(PurchaseReceiptEntity.class);
        PurchaseReturnEntity purchaseReturn = mock(PurchaseReturnEntity.class);
        when(salesPurchaseService.applyPurchaseOrderScope(
                purchaseOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(purchaseOrderWrapper);
        when(salesPurchaseService.applySalesOrderScope(
                salesOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(salesOrderWrapper);
        when(salesPurchaseService.applySalesDeliveryScope(
                salesDeliveryWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(salesDeliveryWrapper);
        when(salesPurchaseService.applySalesReturnScope(
                salesReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(salesReturnWrapper);
        when(salesPurchaseService.applyPurchaseReceiptScope(
                purchaseReceiptWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(purchaseReceiptWrapper);
        when(salesPurchaseService.applyPurchaseReturnScope(
                purchaseReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .thenReturn(purchaseReturnWrapper);

        assertThat(facade.applyPurchaseOrderScope(
                purchaseOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(purchaseOrderWrapper);
        facade.assertCanViewPurchaseOrder(purchaseOrder, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applySalesOrderScope(
                salesOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(salesOrderWrapper);
        facade.assertCanViewSalesOrder(salesOrder, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applySalesDeliveryScope(
                salesDeliveryWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(salesDeliveryWrapper);
        facade.assertCanViewSalesDelivery(salesDelivery, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applySalesReturnScope(
                salesReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(salesReturnWrapper);
        facade.assertCanViewSalesReturn(salesReturn, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applyPurchaseReceiptScope(
                purchaseReceiptWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(purchaseReceiptWrapper);
        facade.assertCanViewPurchaseReceipt(purchaseReceipt, currentUser, snapshot, 31L, 37L);
        assertThat(facade.applyPurchaseReturnScope(
                purchaseReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds))
                .isSameAs(purchaseReturnWrapper);
        facade.assertCanViewPurchaseReturn(purchaseReturn, currentUser, snapshot, 31L, 37L);

        verify(salesPurchaseService).applyPurchaseOrderScope(
                purchaseOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewPurchaseOrder(
                purchaseOrder, currentUser, snapshot, 31L, 37L);
        verify(salesPurchaseService).applySalesOrderScope(
                salesOrderWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewSalesOrder(
                salesOrder, currentUser, snapshot, 31L, 37L);
        verify(salesPurchaseService).applySalesDeliveryScope(
                salesDeliveryWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewSalesDelivery(
                salesDelivery, currentUser, snapshot, 31L, 37L);
        verify(salesPurchaseService).applySalesReturnScope(
                salesReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewSalesReturn(
                salesReturn, currentUser, snapshot, 31L, 37L);
        verify(salesPurchaseService).applyPurchaseReceiptScope(
                purchaseReceiptWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewPurchaseReceipt(
                purchaseReceipt, currentUser, snapshot, 31L, 37L);
        verify(salesPurchaseService).applyPurchaseReturnScope(
                purchaseReturnWrapper, currentUser, snapshot, deptUserIds, postUserIds);
        verify(salesPurchaseService).assertCanViewPurchaseReturn(
                purchaseReturn, currentUser, snapshot, 31L, 37L);
    }

    @Test
    void compatibilityConstructorsRemainAvailable() throws NoSuchMethodException {
        assertThat(DataScopeService.class.getDeclaredConstructor(DataScopeSnapshotService.class)).isNotNull();
        assertThat(DataScopeService.class.getDeclaredConstructor(
                DataScopeSnapshotService.class,
                InventoryDataScopeService.class
        )).isNotNull();
        assertThat(DataScopeService.class.getDeclaredConstructor(
                UserRoleMapper.class,
                RoleMapper.class,
                RoleDataScopeMapper.class,
                UserDataScopeMapper.class
        )).isNotNull();
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap((Constructor<?> constructor) -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

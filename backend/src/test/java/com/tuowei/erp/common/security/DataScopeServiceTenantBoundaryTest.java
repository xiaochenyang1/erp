package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );
    private static final DataScopeSnapshot ALL_SCOPE = DataScopeSnapshot.all();

    private final DataScopeService dataScopeService = new DataScopeService(null, null, null, null);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(SalesReturnEntity.class);
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(PurchaseReturnEntity.class);
    }

    @Test
    void documentScopeQueriesIncludeCompanyAndAccountBook() {
        assertTenantScoped(dataScopeService.applyPurchaseOrderScope(
                new LambdaQueryWrapper<>(PurchaseOrderEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
        assertTenantScoped(dataScopeService.applySalesOrderScope(
                new LambdaQueryWrapper<>(SalesOrderEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
        assertTenantScoped(dataScopeService.applySalesDeliveryScope(
                new LambdaQueryWrapper<>(SalesDeliveryEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
        assertTenantScoped(dataScopeService.applySalesReturnScope(
                new LambdaQueryWrapper<>(SalesReturnEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
        assertTenantScoped(dataScopeService.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<>(PurchaseReceiptEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
        assertTenantScoped(dataScopeService.applyPurchaseReturnScope(
                new LambdaQueryWrapper<>(PurchaseReturnEntity.class), CURRENT_USER, ALL_SCOPE, Set.of(), Set.of()));
    }

    @Test
    void documentViewAssertionsRejectDifferentAccountBookWithinSameCompany() {
        assertDenied(() -> dataScopeService.assertCanViewPurchaseOrder(
                purchaseOrder(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewSalesOrder(
                salesOrder(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewSalesDelivery(
                salesDelivery(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewSalesReturn(
                salesReturn(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewPurchaseReceipt(
                purchaseReceipt(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewPurchaseReturn(
                purchaseReturn(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewInventoryTransfer(
                inventoryTransfer(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
        assertDenied(() -> dataScopeService.assertCanViewProductionOrder(
                productionOrder(CURRENT_USER.companyId(), 9999L), CURRENT_USER, ALL_SCOPE, null, null));
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private void assertDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AccessDeniedException.class);
    }

    private PurchaseOrderEntity purchaseOrder(Long companyId, Long accountBookId) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private SalesOrderEntity salesOrder(Long companyId, Long accountBookId) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private SalesDeliveryEntity salesDelivery(Long companyId, Long accountBookId) {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private SalesReturnEntity salesReturn(Long companyId, Long accountBookId) {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private PurchaseReceiptEntity purchaseReceipt(Long companyId, Long accountBookId) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private PurchaseReturnEntity purchaseReturn(Long companyId, Long accountBookId) {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private InventoryTransferEntity inventoryTransfer(Long companyId, Long accountBookId) {
        InventoryTransferEntity entity = new InventoryTransferEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private ProductionOrderEntity productionOrder(Long companyId, Long accountBookId) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

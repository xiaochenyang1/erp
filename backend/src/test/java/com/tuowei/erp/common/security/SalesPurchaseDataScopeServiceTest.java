package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesPurchaseDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );

    private final SalesPurchaseDataScopeService service = new SalesPurchaseDataScopeService();

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
    void allSalesPurchaseQueriesRetainTenantScope() {
        assertTenantScoped(service.applyPurchaseOrderScope(
                new LambdaQueryWrapper<>(PurchaseOrderEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
        assertTenantScoped(service.applySalesOrderScope(
                new LambdaQueryWrapper<>(SalesOrderEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
        assertTenantScoped(service.applySalesDeliveryScope(
                new LambdaQueryWrapper<>(SalesDeliveryEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
        assertTenantScoped(service.applySalesReturnScope(
                new LambdaQueryWrapper<>(SalesReturnEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
        assertTenantScoped(service.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<>(PurchaseReceiptEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
        assertTenantScoped(service.applyPurchaseReturnScope(
                new LambdaQueryWrapper<>(PurchaseReturnEntity.class),
                CURRENT_USER, DataScopeSnapshot.all(), Set.of(), Set.of()));
    }

    @Test
    void orderQueriesUseCreatorScopeAndDenyEmptyScope() {
        DataScopeSnapshot selfScope = new DataScopeSnapshot(false, false, false, true, Set.of());
        LambdaQueryWrapper<PurchaseOrderEntity> purchaseOrder = service.applyPurchaseOrderScope(
                new LambdaQueryWrapper<>(PurchaseOrderEntity.class),
                CURRENT_USER,
                selfScope,
                Set.of(),
                Set.of()
        );
        LambdaQueryWrapper<SalesOrderEntity> salesOrder = service.applySalesOrderScope(
                new LambdaQueryWrapper<>(SalesOrderEntity.class),
                CURRENT_USER,
                DataScopeSnapshot.none(),
                Set.of(),
                Set.of()
        );

        assertThat(purchaseOrder.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "created_by");
        assertThat(salesOrder.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void fulfillmentQueriesCombineCreatorAndWarehouseScopes() {
        DataScopeSnapshot selfAndWarehouse = new DataScopeSnapshot(
                false, false, false, true, Set.of(31L));
        LambdaQueryWrapper<SalesDeliveryEntity> salesDelivery = service.applySalesDeliveryScope(
                new LambdaQueryWrapper<>(SalesDeliveryEntity.class),
                CURRENT_USER,
                selfAndWarehouse,
                Set.of(),
                Set.of()
        );
        LambdaQueryWrapper<PurchaseReceiptEntity> purchaseReceipt = service.applyPurchaseReceiptScope(
                new LambdaQueryWrapper<>(PurchaseReceiptEntity.class),
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, false, Set.of(37L)),
                Set.of(),
                Set.of()
        );
        LambdaQueryWrapper<SalesReturnEntity> salesReturn = service.applySalesReturnScope(
                new LambdaQueryWrapper<>(SalesReturnEntity.class),
                CURRENT_USER,
                DataScopeSnapshot.none(),
                Set.of(),
                Set.of()
        );

        assertThat(salesDelivery.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "created_by", "warehouse_id", "or");
        assertThat(purchaseReceipt.getSqlSegment().toLowerCase(Locale.ROOT)).contains("warehouse_id");
        assertThat(salesReturn.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void orderViewAssertionsHonorSelfDepartmentAndPostScopes() {
        PurchaseOrderEntity purchaseOrder = purchaseOrder(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), CURRENT_USER.userId());
        SalesOrderEntity salesOrder = salesOrder(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 9999L);

        assertThatCode(() -> service.assertCanViewPurchaseOrder(
                purchaseOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewSalesOrder(
                salesOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                CURRENT_USER.deptId(),
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewSalesOrder(
                salesOrder,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, true, false, Set.of()),
                null,
                CURRENT_USER.postId()
        )).doesNotThrowAnyException();
        assertDenied(() -> service.assertCanViewSalesOrder(
                salesOrder, CURRENT_USER, DataScopeSnapshot.none(), null, null));
    }

    @Test
    void fulfillmentViewAssertionsAllowCreatorOrWarehouseScope() {
        SalesDeliveryEntity salesDelivery = salesDelivery(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 99L, CURRENT_USER.userId());
        PurchaseReceiptEntity purchaseReceipt = purchaseReceipt(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 31L, 9999L);
        SalesReturnEntity salesReturn = salesReturn(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 99L, 9999L);
        PurchaseReturnEntity purchaseReturn = purchaseReturn(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 99L, 9999L);

        assertThatCode(() -> service.assertCanViewSalesDelivery(
                salesDelivery,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, true, Set.of()),
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewPurchaseReceipt(
                purchaseReceipt,
                CURRENT_USER,
                new DataScopeSnapshot(false, false, false, false, Set.of(31L)),
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewPurchaseReturn(
                purchaseReturn,
                CURRENT_USER,
                new DataScopeSnapshot(false, true, false, false, Set.of()),
                CURRENT_USER.deptId(),
                null
        )).doesNotThrowAnyException();
        assertDenied(() -> service.assertCanViewSalesReturn(
                salesReturn, CURRENT_USER, DataScopeSnapshot.none(), null, null));
    }

    @Test
    void tenantProtectionRunsBeforeAllScopeAcceptance() {
        assertDenied(() -> service.assertCanViewPurchaseOrder(
                purchaseOrder(9999L, CURRENT_USER.accountBookId(), CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewSalesOrder(
                salesOrder(CURRENT_USER.companyId(), 9999L, CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewSalesDelivery(
                salesDelivery(9999L, CURRENT_USER.accountBookId(), 31L, CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewSalesReturn(
                salesReturn(CURRENT_USER.companyId(), 9999L, 31L, CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewPurchaseReceipt(
                purchaseReceipt(9999L, CURRENT_USER.accountBookId(), 31L, CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewPurchaseReturn(
                purchaseReturn(CURRENT_USER.companyId(), 9999L, 31L, CURRENT_USER.userId()),
                CURRENT_USER, DataScopeSnapshot.all(), null, null));
    }

    private static void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id");
    }

    private static void assertDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AccessDeniedException.class);
    }

    private static PurchaseOrderEntity purchaseOrder(Long companyId, Long accountBookId, Long createdBy) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static SalesOrderEntity salesOrder(Long companyId, Long accountBookId, Long createdBy) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static SalesDeliveryEntity salesDelivery(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static SalesReturnEntity salesReturn(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static PurchaseReceiptEntity purchaseReceipt(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static PurchaseReturnEntity purchaseReturn(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
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

package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );

    private final InventoryDataScopeService service = new InventoryDataScopeService();

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryTransferEntity.class);
        initTableInfo(InventoryAdjustmentEntity.class);
        initTableInfo(InventoryStockCheckEntity.class);
    }

    @Test
    void warehouseOnlyFiltersSupportAllSelectedAndEmptyScopes() {
        LambdaQueryWrapper<InventoryBalanceEntity> allScope = service.applyInventoryBalanceScope(
                new LambdaQueryWrapper<>(InventoryBalanceEntity.class), DataScopeSnapshot.all());
        LambdaQueryWrapper<InventoryBalanceEntity> selectedScope = service.applyInventoryBalanceScope(
                new LambdaQueryWrapper<>(InventoryBalanceEntity.class), snapshot(Set.of(31L, 37L)));
        LambdaQueryWrapper<InventoryBalanceEntity> emptyScope = service.applyInventoryBalanceScope(
                new LambdaQueryWrapper<>(InventoryBalanceEntity.class), DataScopeSnapshot.none());

        assertThat(allScope.getSqlSegment()).isEmpty();
        assertThat(selectedScope.getSqlSegment().toLowerCase(Locale.ROOT)).contains("warehouse_id", "in");
        assertThat(emptyScope.getSqlSegment()).contains("1 = 0");
    }

    @Test
    void warehouseOnlyViewAssertionsAllowSelectedWarehouseAndDenyOthers() {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setWarehouseId(31L);

        assertThatCode(() -> service.assertCanViewInventoryBalance(entity, snapshot(Set.of(31L))))
                .doesNotThrowAnyException();
        assertDenied(() -> service.assertCanViewInventoryBalance(entity, snapshot(Set.of(37L))));
        assertThatCode(() -> service.assertCanViewInventoryBalance(entity, DataScopeSnapshot.all()))
                .doesNotThrowAnyException();
    }

    @Test
    void transferWarehouseScopeRequiresBothSourceAndDestinationWarehouses() {
        InventoryTransferEntity transfer = inventoryTransfer(CURRENT_USER.companyId(), CURRENT_USER.accountBookId());
        transfer.setFromWarehouseId(31L);
        transfer.setToWarehouseId(37L);

        DataScopeSnapshot bothWarehouses = snapshot(Set.of(31L, 37L));
        LambdaQueryWrapper<InventoryTransferEntity> wrapper = service.applyInventoryTransferScope(
                new LambdaQueryWrapper<>(InventoryTransferEntity.class),
                CURRENT_USER,
                bothWarehouses,
                Set.of(),
                Set.of()
        );

        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "from_warehouse_id", "to_warehouse_id");
        assertThatCode(() -> service.assertCanViewInventoryTransfer(
                transfer, CURRENT_USER, bothWarehouses, null, null)).doesNotThrowAnyException();
        assertDenied(() -> service.assertCanViewInventoryTransfer(
                transfer, CURRENT_USER, snapshot(Set.of(31L)), null, null));
    }

    @Test
    void adjustmentAndStockCheckAllowCreatorOrWarehouseScope() {
        InventoryAdjustmentEntity adjustment = inventoryAdjustment(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 99L, CURRENT_USER.userId());
        InventoryStockCheckEntity stockCheck = inventoryStockCheck(
                CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 31L, 9999L);
        DataScopeSnapshot selfScope = new DataScopeSnapshot(false, false, false, true, Set.of());
        DataScopeSnapshot warehouseScope = snapshot(Set.of(31L));

        assertThatCode(() -> service.assertCanViewInventoryAdjustment(
                adjustment, CURRENT_USER, selfScope, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertCanViewInventoryStockCheck(
                stockCheck, CURRENT_USER, warehouseScope, null, null)).doesNotThrowAnyException();
        assertDenied(() -> service.assertCanViewInventoryAdjustment(
                adjustment, CURRENT_USER, DataScopeSnapshot.none(), null, null));
        assertDenied(() -> service.assertCanViewInventoryStockCheck(
                stockCheck, CURRENT_USER, DataScopeSnapshot.none(), null, null));
    }

    @Test
    void tenantProtectionRunsBeforeAllScopeAcceptance() {
        InventoryTransferEntity transfer = inventoryTransfer(9999L, CURRENT_USER.accountBookId());
        InventoryAdjustmentEntity adjustment = inventoryAdjustment(
                CURRENT_USER.companyId(), 9999L, 31L, CURRENT_USER.userId());
        InventoryStockCheckEntity stockCheck = inventoryStockCheck(
                9999L, CURRENT_USER.accountBookId(), 31L, CURRENT_USER.userId());

        assertDenied(() -> service.assertCanViewInventoryTransfer(
                transfer, CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewInventoryAdjustment(
                adjustment, CURRENT_USER, DataScopeSnapshot.all(), null, null));
        assertDenied(() -> service.assertCanViewInventoryStockCheck(
                stockCheck, CURRENT_USER, DataScopeSnapshot.all(), null, null));
    }

    private static DataScopeSnapshot snapshot(Set<Long> warehouseIds) {
        return new DataScopeSnapshot(false, false, false, false, warehouseIds);
    }

    private static InventoryTransferEntity inventoryTransfer(Long companyId, Long accountBookId) {
        InventoryTransferEntity entity = new InventoryTransferEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }

    private static InventoryAdjustmentEntity inventoryAdjustment(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        InventoryAdjustmentEntity entity = new InventoryAdjustmentEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static InventoryStockCheckEntity inventoryStockCheck(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long createdBy
    ) {
        InventoryStockCheckEntity entity = new InventoryStockCheckEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(warehouseId);
        entity.setCreatedBy(createdBy);
        return entity;
    }

    private static void assertDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AccessDeniedException.class);
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

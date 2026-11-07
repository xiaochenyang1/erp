package com.tuowei.erp.inventory.alert;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertDispositionEntity;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertQueryService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class InventoryAlertQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 13, 10, 0)
    );
    private static final Long WAREHOUSE_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;

    private final InventoryAlertRuleMapper alertRuleMapper = mock(InventoryAlertRuleMapper.class);
    private final InventoryAlertDispositionMapper dispositionMapper = mock(InventoryAlertDispositionMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryAlertRuleEntity.class);
        initTableInfo(InventoryAlertDispositionEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
    }

    @Test
    void listRulesAppliesFiltersAndHydratesTenantDisplayData() {
        stubAudit();
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule()));
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(warehouse()));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(product()));

        var result = service().listRules(WAREHOUSE_ID, PRODUCT_ID, true);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.warehouseName()).isEqualTo("主仓");
            assertThat(response.productCode()).isEqualTo("MAT-7001");
            assertThat(response.productName()).isEqualTo("原料A");
            assertThat(response.enabled()).isTrue();
        });
        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("warehouse_id")
                .contains("product_id")
                .contains("enabled")
                .contains("order by");
    }

    @Test
    void listRulesSkipsHydrationForEmptyResult() {
        stubAudit();
        when(alertRuleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service().listRules(null, null, null)).isEmpty();

        verify(warehouseMapper, never()).selectBatchIds(any(Collection.class));
        verify(productMapper, never()).selectBatchIds(any(Collection.class));
    }

    @Test
    void listLowStockBatchesBalancesAndOverlaysDispositionStatus() {
        stubAudit();
        InventoryAlertDispositionEntity ignored = disposition("IGNORED", "7.0000");
        when(dispositionMapper.selectList(any())).thenReturn(List.of(ignored));
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule()));
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(balance("3.0000")));
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(warehouse()));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(product()));

        var result = service().listLowStock(null, null);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.currentQuantity()).isEqualByComparingTo("3.0000");
            assertThat(response.shortageQty()).isEqualByComparingTo("7.0000");
            assertThat(response.status()).isEqualTo("IGNORED");
            assertThat(response.warehouseName()).isEqualTo("主仓");
            assertThat(response.productName()).isEqualTo("原料A");
        });
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> balanceCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectList(balanceCaptor.capture());
        assertThat(balanceCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("warehouse_id")
                .contains("product_id");
    }

    @Test
    void listLowStockReactivatesWhenShortageWorsensAndDropsRecoveredStock() {
        stubAudit();
        InventoryAlertRuleEntity activeAlert = rule();
        InventoryAlertRuleEntity recovered = rule();
        recovered.setId(9102L);
        recovered.setProductId(7002L);
        when(dispositionMapper.selectList(any())).thenReturn(List.of(disposition("RESOLVED", "5.0000")));
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(activeAlert, recovered));
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(
                balance(PRODUCT_ID, "3.0000"),
                balance(7002L, "12.0000")
        ));
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(warehouse()));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(product(), product(7002L)));

        var result = service().listLowStock(null, null, AUDIT);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.productId()).isEqualTo(PRODUCT_ID);
            assertThat(response.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void listLowStockDropsCrossTenantDisplayRows() {
        stubAudit();
        when(dispositionMapper.selectList(any())).thenReturn(List.of());
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(rule()));
        when(inventoryBalanceMapper.selectList(any())).thenReturn(List.of(balance("3.0000")));
        WarehouseEntity crossTenantWarehouse = warehouse();
        crossTenantWarehouse.setAccountBookId(999L);
        ProductEntity crossTenantProduct = product();
        crossTenantProduct.setCompanyId(999L);
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(crossTenantWarehouse));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(crossTenantProduct));

        var result = service().listLowStock(null, null);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.warehouseName()).isNull();
            assertThat(response.productCode()).isNull();
            assertThat(response.productName()).isNull();
        });
    }

    @Test
    void listLowStockAppliesWarehouseScopeBeforeRuleQuery() {
        stubAudit();
        when(alertRuleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service().listLowStock(null, null, AUDIT, Set.of(WAREHOUSE_ID))).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("warehouse_id");
    }

    @Test
    void listLowStockRejectsEmptyWarehouseScopeBeforeRuleQuery() {
        stubAudit();

        assertThat(service().listLowStock(null, null, AUDIT, Set.of())).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("1 = 0");
    }

    private InventoryAlertQueryService service() {
        return new InventoryAlertQueryService(
                alertRuleMapper,
                dispositionMapper,
                auditMetadataFactory,
                warehouseMapper,
                productMapper,
                inventoryBalanceMapper
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private InventoryAlertRuleEntity rule() {
        InventoryAlertRuleEntity rule = new InventoryAlertRuleEntity();
        rule.setId(9101L);
        rule.setCompanyId(AUDIT.companyId());
        rule.setAccountBookId(AUDIT.accountBookId());
        rule.setWarehouseId(WAREHOUSE_ID);
        rule.setProductId(PRODUCT_ID);
        rule.setMinQty(new BigDecimal("10.0000"));
        rule.setEnabled(1);
        rule.setDeletedFlag(0);
        rule.setRemark("low stock");
        rule.setCreatedTime(AUDIT.now());
        return rule;
    }

    private InventoryAlertDispositionEntity disposition(String status, String shortage) {
        InventoryAlertDispositionEntity entity = new InventoryAlertDispositionEntity();
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setProductId(PRODUCT_ID);
        entity.setStatus(status);
        entity.setSnapshotShortageQty(new BigDecimal(shortage));
        entity.setDeletedFlag(0);
        return entity;
    }

    private InventoryBalanceEntity balance(String qty) {
        return balance(PRODUCT_ID, qty);
    }

    private InventoryBalanceEntity balance(Long productId, String qty) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setProductId(productId);
        entity.setQtyOnHand(new BigDecimal(qty));
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseName("主仓");
        return entity;
    }

    private ProductEntity product() {
        return product(PRODUCT_ID);
    }

    private ProductEntity product(Long id) {
        ProductEntity entity = new ProductEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setProductCode("MAT-" + id);
        entity.setProductName("原料A");
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

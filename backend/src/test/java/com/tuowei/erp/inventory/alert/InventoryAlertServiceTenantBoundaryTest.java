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
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleUpdateRequest;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAlertServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final Long WAREHOUSE_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;

    @Mock
    private InventoryAlertRuleMapper alertRuleMapper;

    @Mock
    private InventoryAlertDispositionMapper dispositionMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryBalanceMapper inventoryBalanceMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryAlertRuleEntity.class);
        initTableInfo(InventoryAlertDispositionEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(ProductEntity.class);
        initTableInfo(InventoryBalanceEntity.class);
    }

    @Test
    void listLowStockScopesRulesByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectList(any())).thenReturn(List.of());

        service().listLowStock(null, null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void listLowStockReturnsDisplayFieldsForFrontendAlertsPage() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectList(any())).thenReturn(List.of(alertRule()));
        when(inventoryBalanceMapper.selectList(any()))
                .thenReturn(List.of(balance(new BigDecimal("3.0000"))));
        when(warehouseMapper.selectBatchIds(any()))
                .thenReturn(List.of(activeWarehouse(AUDIT.accountBookId())));
        when(productMapper.selectBatchIds(any()))
                .thenReturn(List.of(activeProduct(AUDIT.accountBookId())));

        Object response = service().listLowStock(null, null).get(0);

        assertThat(recordComponentNames(response))
                .contains("id", "warehouseName", "productCode", "productName", "currentQuantity",
                        "minQuantity", "alertType", "status");
        assertThat(readRecordComponent(response, "id")).isEqualTo(9101L);
        assertThat(readRecordComponent(response, "warehouseName")).isEqualTo("主仓");
        assertThat(readRecordComponent(response, "productCode")).isEqualTo("MAT-001");
        assertThat(readRecordComponent(response, "productName")).isEqualTo("原料A");
        assertThat(readRecordComponent(response, "currentQuantity")).isEqualTo(new BigDecimal("3.0000"));
        assertThat(readRecordComponent(response, "minQuantity")).isEqualTo(new BigDecimal("10.0000"));
        assertThat(readRecordComponent(response, "alertType")).isEqualTo("LOW_STOCK");
        assertThat(readRecordComponent(response, "status")).isEqualTo("ACTIVE");
    }

    @Test
    void createRuleRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(9999L));

        assertThatThrownBy(() -> service().createRule(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRuleRejectsProductFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(AUDIT.accountBookId()));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct(9999L));

        assertThatThrownBy(() -> service().createRule(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void listRulesScopesByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectList(any())).thenReturn(List.of());

        service().listRules(null, null, null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void updateRuleRejectsMissingTenantRule() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectById(9101L)).thenReturn(null);

        assertThatThrownBy(() -> service().updateRule(9101L, new InventoryAlertRuleUpdateRequest(
                new BigDecimal("12.0000"), "missing"
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("低库存规则不存在");
    }

    @Test
    void disableRuleUpdatesEnabledFlagForTenantRule() {
        InventoryAlertRuleEntity rule = alertRule();
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectById(9101L)).thenReturn(rule);
        when(alertRuleMapper.updateById(any(InventoryAlertRuleEntity.class))).thenReturn(1);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(AUDIT.accountBookId()));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct(AUDIT.accountBookId()));

        Object response = service().disableRule(9101L);

        ArgumentCaptor<InventoryAlertRuleEntity> captor = ArgumentCaptor.forClass(InventoryAlertRuleEntity.class);
        verify(alertRuleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(0);
        assertThat(readRecordComponent(response, "enabled")).isEqualTo(false);
        assertThat(readRecordComponent(response, "warehouseName")).isEqualTo("主仓");
        assertThat(readRecordComponent(response, "productCode")).isEqualTo("MAT-001");
    }

    @Test
    void reactivateLowStockSoftDeletesExistingDisposition() {
        InventoryAlertDispositionEntity disposition = new InventoryAlertDispositionEntity();
        disposition.setId(9201L);
        disposition.setCompanyId(AUDIT.companyId());
        disposition.setAccountBookId(AUDIT.accountBookId());
        disposition.setWarehouseId(WAREHOUSE_ID);
        disposition.setProductId(PRODUCT_ID);
        disposition.setStatus("IGNORED");
        disposition.setDeletedFlag(0);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(dispositionMapper.selectOne(any())).thenReturn(disposition);

        service().reactivate(WAREHOUSE_ID, PRODUCT_ID);

        ArgumentCaptor<InventoryAlertDispositionEntity> captor =
                ArgumentCaptor.forClass(InventoryAlertDispositionEntity.class);
        verify(dispositionMapper).updateById(captor.capture());
        InventoryAlertDispositionEntity updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(9201L);
        assertThat(updated.getDeletedFlag()).isEqualTo(1);
        assertThat(updated.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(updated.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    private InventoryAlertService service() {
        return new InventoryAlertService(
                alertRuleMapper,
                dispositionMapper,
                inventoryPostingService,
                auditMetadataFactory,
                warehouseMapper,
                productMapper,
                inventoryBalanceMapper
        );
    }

    private InventoryAlertRuleCreateRequest createRequest() {
        return new InventoryAlertRuleCreateRequest(
                WAREHOUSE_ID,
                PRODUCT_ID,
                new BigDecimal("10.0000"),
                "tenant boundary"
        );
    }

    private InventoryAlertRuleEntity alertRule() {
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
        return rule;
    }

    private WarehouseEntity activeWarehouse(Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCompanyId(AUDIT.companyId());
        warehouse.setAccountBookId(accountBookId);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("主仓");
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private ProductEntity activeProduct(Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(accountBookId);
        product.setProductCode("MAT-001");
        product.setProductName("原料A");
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
    }

    private InventoryBalanceEntity balance(BigDecimal qtyOnHand) {
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setCompanyId(AUDIT.companyId());
        balance.setAccountBookId(AUDIT.accountBookId());
        balance.setWarehouseId(WAREHOUSE_ID);
        balance.setProductId(PRODUCT_ID);
        balance.setQtyOnHand(qtyOnHand);
        return balance;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    private static List<String> recordComponentNames(Object record) {
        return Arrays.stream(record.getClass().getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static Object readRecordComponent(Object record, String componentName) {
        try {
            RecordComponent component = Arrays.stream(record.getClass().getRecordComponents())
                    .filter(item -> item.getName().equals(componentName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing record component: " + componentName));
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read record component: " + componentName, e);
        }
    }
}

package com.tuowei.erp.inventory.alert;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertDispositionEntity;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertCommandService;
import com.tuowei.erp.inventory.alert.service.InventoryAlertQueryService;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAlertCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 15, 30)
    );
    private static final Long WAREHOUSE_ID = 1001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long RULE_ID = 3001L;
    private static final Long DISPOSITION_ID = 4001L;

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
    private InventoryAlertQueryService alertQueryService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryAlertRuleEntity.class);
        initTableInfo(InventoryAlertDispositionEntity.class);
    }

    @Test
    void createBuildsTenantScopedRuleWithRoundedQuantityAndTrimmedRemark() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        WarehouseEntity warehouse = warehouse();
        ProductEntity product = product();
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(alertRuleMapper.selectOne(any())).thenReturn(null);
        when(alertRuleMapper.insert(any(InventoryAlertRuleEntity.class))).thenAnswer(invocation -> {
            InventoryAlertRuleEntity rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            return 1;
        });
        InventoryAlertRuleResponse expected = response(true);
        when(alertQueryService.toRuleResponse(any(InventoryAlertRuleEntity.class), eq(warehouse), eq(product)))
                .thenReturn(expected);

        InventoryAlertRuleResponse actual = service().createRule(new InventoryAlertRuleCreateRequest(
                WAREHOUSE_ID,
                PRODUCT_ID,
                new BigDecimal("12.34567"),
                "  安全库存  "
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<InventoryAlertRuleEntity> captor = ArgumentCaptor.forClass(InventoryAlertRuleEntity.class);
        verify(alertRuleMapper).insert(captor.capture());
        InventoryAlertRuleEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(RULE_ID);
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(inserted.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(inserted.getMinQty()).isEqualByComparingTo("12.3457");
        assertThat(inserted.getEnabled()).isEqualTo(1);
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getRemark()).isEqualTo("安全库存");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
        InOrder order = inOrder(warehouseMapper, productMapper, alertRuleMapper, alertQueryService);
        order.verify(warehouseMapper).selectById(WAREHOUSE_ID);
        order.verify(productMapper).selectById(PRODUCT_ID);
        order.verify(alertRuleMapper).selectOne(any());
        order.verify(alertRuleMapper).insert(any(InventoryAlertRuleEntity.class));
        order.verify(alertQueryService).toRuleResponse(any(), eq(warehouse), eq(product));
    }

    @Test
    void createNormalizesBlankRemarkToNull() {
        stubCreatePrerequisites();
        when(alertRuleMapper.insert(any(InventoryAlertRuleEntity.class))).thenReturn(1);
        when(alertQueryService.toRuleResponse(any(), any(), any())).thenReturn(response(true));

        service().createRule(new InventoryAlertRuleCreateRequest(
                WAREHOUSE_ID, PRODUCT_ID, new BigDecimal("1"), "  \t"
        ));

        ArgumentCaptor<InventoryAlertRuleEntity> captor = ArgumentCaptor.forClass(InventoryAlertRuleEntity.class);
        verify(alertRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getRemark()).isNull();
    }

    @Test
    void createRejectsDuplicateIncludingDisabledRuleBeforeInsert() {
        stubCreatePrerequisites();
        InventoryAlertRuleEntity disabled = rule();
        disabled.setEnabled(0);
        when(alertRuleMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> service().createRule(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该仓库商品已存在低库存规则");

        verify(alertRuleMapper, never()).insert(any(InventoryAlertRuleEntity.class));
        verify(alertQueryService, never()).toRuleResponse(any(), any(), any());
    }

    @Test
    void updateHydratesMasterDataOnlyAfterSuccessfulOptimisticUpdate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity existing = rule();
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(existing);
        when(alertRuleMapper.updateById(existing)).thenReturn(1);
        WarehouseEntity warehouse = warehouse();
        ProductEntity product = product();
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        InventoryAlertRuleResponse expected = response(true);
        when(alertQueryService.toRuleResponse(existing, warehouse, product)).thenReturn(expected);

        InventoryAlertRuleResponse actual = service().updateRule(
                RULE_ID,
                new InventoryAlertRuleUpdateRequest(new BigDecimal("8.88888"), "  更新说明 ")
        );

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getMinQty()).isEqualByComparingTo("8.8889");
        assertThat(existing.getRemark()).isEqualTo("更新说明");
        InOrder order = inOrder(alertRuleMapper, warehouseMapper, productMapper, alertQueryService);
        order.verify(alertRuleMapper).updateById(existing);
        order.verify(warehouseMapper).selectById(WAREHOUSE_ID);
        order.verify(productMapper).selectById(PRODUCT_ID);
        order.verify(alertQueryService).toRuleResponse(existing, warehouse, product);
        verify(alertRuleMapper, never()).selectOne(any());
    }

    @Test
    void updateStopsOnOptimisticConflictBeforeHydratingMasterData() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity existing = rule();
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(existing);
        when(alertRuleMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().updateRule(
                RULE_ID,
                new InventoryAlertRuleUpdateRequest(new BigDecimal("8"), "x")
        )).isInstanceOf(BusinessConflictException.class)
                .hasMessage("低库存规则已被其他操作修改，请刷新后重试");

        verify(warehouseMapper, never()).selectById(any());
        verify(productMapper, never()).selectById(any());
        verify(alertQueryService, never()).toRuleResponse(any(), any(), any());
    }

    @Test
    void updateRejectsRuleOutsideCurrentTenant() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity foreign = rule();
        foreign.setAccountBookId(9999L);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service().updateRule(
                RULE_ID,
                new InventoryAlertRuleUpdateRequest(new BigDecimal("8"), "x")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("低库存规则不存在");

        verify(alertRuleMapper, never()).updateById(any(InventoryAlertRuleEntity.class));
    }

    @Test
    void enableRejectsDuplicateOtherRuleBeforeWriting() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(rule());
        InventoryAlertRuleEntity other = rule();
        other.setId(9999L);
        when(alertRuleMapper.selectOne(any())).thenReturn(other);

        assertThatThrownBy(() -> service().enableRule(RULE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该仓库商品已存在低库存规则");

        verify(alertRuleMapper, never()).updateById(any(InventoryAlertRuleEntity.class));
    }

    @Test
    void enableSetsFlagAndUsesSelfExcludingUniquenessCheck() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity existing = rule();
        existing.setEnabled(0);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(existing);
        when(alertRuleMapper.selectOne(any())).thenReturn(null);
        when(alertRuleMapper.updateById(existing)).thenReturn(1);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(alertQueryService.toRuleResponse(any(), any(), any())).thenReturn(response(true));

        service().enableRule(RULE_ID);

        assertThat(existing.getEnabled()).isEqualTo(1);
        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertRuleEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alertRuleMapper).selectOne(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "warehouse_id", "product_id", "deleted_flag")
                .contains("id");
        verify(alertRuleMapper).updateById(existing);
    }

    @Test
    void disableDoesNotRunUniquenessQuery() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity existing = rule();
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(existing);
        when(alertRuleMapper.updateById(existing)).thenReturn(1);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(alertQueryService.toRuleResponse(any(), any(), any())).thenReturn(response(false));

        service().disableRule(RULE_ID);

        assertThat(existing.getEnabled()).isEqualTo(0);
        verify(alertRuleMapper, never()).selectOne(any());
        verify(alertRuleMapper).updateById(existing);
    }

    @Test
    void handleRejectsInvalidStatusBeforeAnyRead() {
        assertThatThrownBy(() -> service().handle(WAREHOUSE_ID, PRODUCT_ID, " unknown ", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处置状态只能为 IGNORED 或 RESOLVED");
        assertThatThrownBy(() -> service().handle(WAREHOUSE_ID, PRODUCT_ID, null, "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处置状态只能为 IGNORED 或 RESOLVED");
        verifyNoAlertReads();
    }

    @Test
    void handleRejectsMissingActiveRule() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().handle(WAREHOUSE_ID, PRODUCT_ID, "IGNORED", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("低库存规则不存在或已停用");

        verify(inventoryPostingService, never()).getQtyOnHand(anyLong(), anyLong(), anyLong(), anyLong());
        verify(dispositionMapper, never()).selectOne(any());
    }

    @Test
    void handleRejectsInventoryAtThresholdBeforeDispositionRead() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectOne(any())).thenReturn(rule());
        when(inventoryPostingService.getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        )).thenReturn(new BigDecimal("10.0000"));

        assertThatThrownBy(() -> service().handle(WAREHOUSE_ID, PRODUCT_ID, "RESOLVED", "r"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前库存已高于安全库存，无需处置");

        verify(dispositionMapper, never()).selectOne(any());
        verify(dispositionMapper, never()).insert(any(InventoryAlertDispositionEntity.class));
    }

    @Test
    void handleCreatesDispositionWithNormalizedStatusRawRemarkAndShortageSnapshot() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertRuleEntity rule = rule();
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        when(inventoryPostingService.getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        )).thenReturn(new BigDecimal("3.3333"));
        when(dispositionMapper.selectOne(any())).thenReturn(null);
        when(dispositionMapper.insert(any(InventoryAlertDispositionEntity.class))).thenAnswer(invocation -> {
            InventoryAlertDispositionEntity entity = invocation.getArgument(0);
            entity.setId(DISPOSITION_ID);
            return 1;
        });

        service().handle(WAREHOUSE_ID, PRODUCT_ID, "  resolved ", "  原样备注  ");

        ArgumentCaptor<InventoryAlertDispositionEntity> captor =
                ArgumentCaptor.forClass(InventoryAlertDispositionEntity.class);
        verify(dispositionMapper).insert(captor.capture());
        InventoryAlertDispositionEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(DISPOSITION_ID);
        assertThat(inserted.getStatus()).isEqualTo("RESOLVED");
        assertThat(inserted.getSnapshotShortageQty()).isEqualByComparingTo("6.6667");
        assertThat(inserted.getHandleRemark()).isEqualTo("  原样备注  ");
        assertThat(inserted.getRuleId()).isEqualTo(RULE_ID);
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getHandledBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getHandledTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getVersion()).isZero();
        InOrder order = inOrder(alertRuleMapper, inventoryPostingService, dispositionMapper);
        order.verify(alertRuleMapper).selectOne(any());
        order.verify(inventoryPostingService).getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        );
        order.verify(dispositionMapper).selectOne(any());
        order.verify(dispositionMapper).insert(any(InventoryAlertDispositionEntity.class));
    }

    @Test
    void handleUpdatesExistingDispositionWithoutInserting() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectOne(any())).thenReturn(rule());
        when(inventoryPostingService.getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()
        )).thenReturn(new BigDecimal("2"));
        InventoryAlertDispositionEntity existing = disposition();
        when(dispositionMapper.selectOne(any())).thenReturn(existing);

        service().handle(WAREHOUSE_ID, PRODUCT_ID, "IGNORED", "new remark");

        assertThat(existing.getStatus()).isEqualTo("IGNORED");
        assertThat(existing.getSnapshotShortageQty()).isEqualByComparingTo("8.0000");
        assertThat(existing.getHandleRemark()).isEqualTo("new remark");
        assertThat(existing.getRuleId()).isEqualTo(RULE_ID);
        verify(dispositionMapper).updateById(existing);
        verify(dispositionMapper, never()).insert(any(InventoryAlertDispositionEntity.class));
    }

    @Test
    void reactivateWithoutDispositionIsSilent() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(dispositionMapper.selectOne(any())).thenReturn(null);

        service().reactivate(WAREHOUSE_ID, PRODUCT_ID);

        verify(dispositionMapper, never()).updateById(any(InventoryAlertDispositionEntity.class));
    }

    @Test
    void reactivateSoftDeletesTenantScopedDisposition() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryAlertDispositionEntity existing = disposition();
        when(dispositionMapper.selectOne(any())).thenReturn(existing);

        service().reactivate(WAREHOUSE_ID, PRODUCT_ID);

        assertThat(existing.getDeletedFlag()).isEqualTo(1);
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
        ArgumentCaptor<LambdaQueryWrapper<InventoryAlertDispositionEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(dispositionMapper).selectOne(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "warehouse_id", "product_id", "deleted_flag");
        verify(dispositionMapper).updateById(existing);
    }

    private InventoryAlertCommandService service() {
        return new InventoryAlertCommandService(
                alertRuleMapper,
                dispositionMapper,
                inventoryPostingService,
                auditMetadataFactory,
                warehouseMapper,
                productMapper,
                alertQueryService
        );
    }

    private void stubCreatePrerequisites() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(alertRuleMapper.selectOne(any())).thenReturn(null);
    }

    private void verifyNoAlertReads() {
        verify(alertRuleMapper, never()).selectOne(any());
        verify(alertRuleMapper, never()).selectById(any());
        verify(dispositionMapper, never()).selectOne(any());
        verify(inventoryPostingService, never()).getQtyOnHand(anyLong(), anyLong(), anyLong(), anyLong());
    }

    private InventoryAlertRuleCreateRequest createRequest() {
        return new InventoryAlertRuleCreateRequest(WAREHOUSE_ID, PRODUCT_ID, new BigDecimal("10"), "remark");
    }

    private InventoryAlertRuleEntity rule() {
        InventoryAlertRuleEntity entity = new InventoryAlertRuleEntity();
        entity.setId(RULE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setProductId(PRODUCT_ID);
        entity.setMinQty(new BigDecimal("10.0000"));
        entity.setEnabled(1);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private InventoryAlertDispositionEntity disposition() {
        InventoryAlertDispositionEntity entity = new InventoryAlertDispositionEntity();
        entity.setId(DISPOSITION_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRuleId(999L);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setProductId(PRODUCT_ID);
        entity.setStatus("RESOLVED");
        entity.setSnapshotShortageQty(new BigDecimal("5.0000"));
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setWarehouseName("主仓");
        return entity;
    }

    private ProductEntity product() {
        ProductEntity entity = new ProductEntity();
        entity.setId(PRODUCT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setProductCode("MAT-001");
        entity.setProductName("原料A");
        return entity;
    }

    private InventoryAlertRuleResponse response(boolean enabled) {
        return new InventoryAlertRuleResponse(
                RULE_ID,
                WAREHOUSE_ID,
                "主仓",
                PRODUCT_ID,
                "MAT-001",
                "原料A",
                new BigDecimal("10.0000"),
                enabled,
                "remark",
                AUDIT.now()
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityClass.getName()
        );
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

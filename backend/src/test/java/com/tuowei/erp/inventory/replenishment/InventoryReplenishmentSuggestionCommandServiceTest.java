package com.tuowei.erp.inventory.replenishment;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.model.InventoryReplenishmentSuggestionEntity;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionCommandService;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionQueryService;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReplenishmentSuggestionCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9100L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 6, 9, 30)
    );
    private static final Long ID = 9001L;
    private static final Long RULE_ID = 7101L;
    private static final Long WAREHOUSE_ID = 8101L;
    private static final Long PRODUCT_ID = 8201L;
    private static final Long SUPPLIER_ID = 8301L;

    @Mock
    private InventoryReplenishmentSuggestionMapper suggestionMapper;
    @Mock
    private InventoryAlertRuleMapper alertRuleMapper;
    @Mock
    private InventoryPostingService inventoryPostingService;
    @Mock
    private InventoryAlertService inventoryAlertService;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private PurchaseOrderService purchaseOrderService;
    @Mock
    private InventoryReplenishmentSuggestionQueryService suggestionQueryService;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createPersistsTenantSnapshotAndResolvesLowStockAlert() {
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(alertRule());
        when(inventoryPostingService.getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(new BigDecimal("3.0000"));
        when(suggestionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectCount(any())).thenReturn(0L);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(suggestionMapper.insert(any(InventoryReplenishmentSuggestionEntity.class))).thenAnswer(invocation -> {
            InventoryReplenishmentSuggestionEntity entity = invocation.getArgument(0);
            entity.setId(ID);
            return 1;
        });
        InventoryReplenishmentSuggestionResponse response = response("DRAFT");
        when(suggestionQueryService.toResponse(any(InventoryReplenishmentSuggestionEntity.class), any(), any(), any(), any()))
                .thenReturn(response);

        InventoryReplenishmentSuggestionResponse actual = service().create(createRequest(new BigDecimal("7.00009")));

        ArgumentCaptor<InventoryReplenishmentSuggestionEntity> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionEntity.class);
        verify(suggestionMapper).insert(captor.capture());
        InventoryReplenishmentSuggestionEntity inserted = captor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getSuggestionNo()).isEqualTo("RS20260706000001");
        assertThat(inserted.getSuggestedQty()).isEqualByComparingTo("7.0001");
        assertThat(inserted.getShortageQtySnapshot()).isEqualByComparingTo("7.0000");
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(actual).isSameAs(response);
        verify(inventoryAlertService).handle(
                WAREHOUSE_ID, PRODUCT_ID, "RESOLVED", "已生成补货建议 RS20260706000001");
    }

    @Test
    void createChecksCurrentStockBeforeSuggestedQuantityAndDuplicateDraft() {
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(alertRule());
        when(inventoryPostingService.getQtyOnHand(
                WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(new BigDecimal("10.0000"));

        assertThatThrownBy(() -> service().create(createRequest(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前库存已高于安全库存，无需生成补货建议");

        verify(suggestionMapper, never()).selectOne(any());
        verify(warehouseMapper, never()).selectById(any());
    }

    @Test
    void createRejectsRuleFromAnotherTenantBeforeReadingStock() {
        InventoryAlertRuleEntity rule = alertRule();
        rule.setCompanyId(999L);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(rule);

        assertThatThrownBy(() -> service().create(createRequest(new BigDecimal("7.0000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("低库存规则不存在或已停用");

        verify(inventoryPostingService, never()).getQtyOnHand(any(), any(), any(), any());
    }

    @Test
    void updateRejectsSupplierFromAnotherTenantBeforeWriting() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        SupplierEntity supplier = activeSupplier();
        supplier.setAccountBookId(999L);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier);

        assertThatThrownBy(() -> service().update(ID, new InventoryReplenishmentSuggestionUpdateRequest(
                SUPPLIER_ID, new BigDecimal("8.0000"), null, "调整")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或已停用");

        verify(suggestionMapper, never()).updateById(any(InventoryReplenishmentSuggestionEntity.class));
    }

    @Test
    void updateChangesDraftFieldsAndReturnsHydratedResponse() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(suggestionMapper.updateById(suggestion)).thenReturn(1);
        InventoryReplenishmentSuggestionResponse response = response("DRAFT");
        when(suggestionQueryService.toResponse(suggestion)).thenReturn(response);

        assertThat(service().update(ID, new InventoryReplenishmentSuggestionUpdateRequest(
                SUPPLIER_ID,
                new BigDecimal("8.50009"),
                LocalDate.of(2026, 7, 15),
                "  调整补货计划  "
        ))).isSameAs(response);

        assertThat(suggestion.getSuggestedQty()).isEqualByComparingTo("8.5001");
        assertThat(suggestion.getExpectedArrivalDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(suggestion.getRemark()).isEqualTo("调整补货计划");
        assertThat(suggestion.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(suggestion.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void updateReturnsConflictWhenOptimisticLockUpdateDoesNotModifyRow() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        when(suggestionMapper.updateById(suggestion)).thenReturn(0);

        assertThatThrownBy(() -> service().update(ID, new InventoryReplenishmentSuggestionUpdateRequest(
                null, new BigDecimal("8.0000"), null, "调整")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("补货建议已被其他操作修改，请刷新后重试");
    }

    @Test
    void cancelAllowsNullRequestAndPreservesExistingRemarkConvention() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        when(suggestionMapper.updateById(suggestion)).thenReturn(1);
        InventoryReplenishmentSuggestionResponse response = response("CANCELLED");
        when(suggestionQueryService.toResponse(suggestion)).thenReturn(response);

        assertThat(service().cancel(ID, null)).isSameAs(response);

        assertThat(suggestion.getStatus()).isEqualTo("CANCELLED");
        assertThat(suggestion.getRemark()).isEqualTo("低库存补货；取消原因：null");
    }

    @Test
    void convertCreatesPurchaseOrderThenMarksSuggestionConverted() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(suggestionMapper.updateById(suggestion)).thenReturn(1);
        InventoryReplenishmentSuggestionResponse response = response("CONVERTED");
        when(suggestionQueryService.toResponse(suggestion)).thenReturn(response);

        assertThat(service().convertToPurchaseOrder(ID)).isSameAs(response);

        assertThat(suggestion.getStatus()).isEqualTo("CONVERTED");
        assertThat(suggestion.getPurchaseOrderId()).isEqualTo(9901L);
        assertThat(suggestion.getPurchaseOrderNo()).isEqualTo("PO202607060001");
        verify(purchaseOrderService).create(any());
        verify(suggestionMapper).updateById(suggestion);
    }

    @Test
    void convertRejectsMissingSupplierBeforeReadingMasterData() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        suggestion.setSupplierId(null);
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择供应商后再转采购订单");

        verify(supplierMapper, never()).selectById(any());
        verify(warehouseMapper, never()).selectById(any());
        verify(purchaseOrderService, never()).create(any());
    }

    @Test
    void convertRejectsInactiveWarehouseBeforeCreatingPurchaseOrder() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        when(suggestionQueryService.requireSuggestion(ID)).thenReturn(suggestion);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        WarehouseEntity inactive = activeWarehouse();
        inactive.setStatus("INACTIVE");
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(inactive);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");

        verify(productMapper, never()).selectById(any());
        verify(purchaseOrderService, never()).create(any());
    }

    private InventoryReplenishmentSuggestionCommandService service() {
        return new InventoryReplenishmentSuggestionCommandService(
                suggestionMapper,
                alertRuleMapper,
                inventoryPostingService,
                inventoryAlertService,
                auditMetadataFactory,
                warehouseMapper,
                productMapper,
                supplierMapper,
                purchaseOrderService,
                suggestionQueryService
        );
    }

    private InventoryReplenishmentSuggestionCreateRequest createRequest(BigDecimal qty) {
        return new InventoryReplenishmentSuggestionCreateRequest(
                RULE_ID, WAREHOUSE_ID, PRODUCT_ID, SUPPLIER_ID, qty,
                LocalDate.of(2026, 7, 12), "低库存补货");
    }

    private InventoryAlertRuleEntity alertRule() {
        InventoryAlertRuleEntity rule = new InventoryAlertRuleEntity();
        rule.setId(RULE_ID);
        rule.setCompanyId(AUDIT.companyId());
        rule.setAccountBookId(AUDIT.accountBookId());
        rule.setWarehouseId(WAREHOUSE_ID);
        rule.setProductId(PRODUCT_ID);
        rule.setMinQty(new BigDecimal("10.0000"));
        rule.setEnabled(1);
        rule.setDeletedFlag(0);
        return rule;
    }

    private InventoryReplenishmentSuggestionEntity draftSuggestion() {
        InventoryReplenishmentSuggestionEntity suggestion = new InventoryReplenishmentSuggestionEntity();
        suggestion.setId(ID);
        suggestion.setCompanyId(AUDIT.companyId());
        suggestion.setAccountBookId(AUDIT.accountBookId());
        suggestion.setSuggestionNo("RS202607060001");
        suggestion.setSourceType("LOW_STOCK_ALERT");
        suggestion.setSourceRuleId(RULE_ID);
        suggestion.setWarehouseId(WAREHOUSE_ID);
        suggestion.setProductId(PRODUCT_ID);
        suggestion.setSupplierId(SUPPLIER_ID);
        suggestion.setSuggestedQty(new BigDecimal("7.0000"));
        suggestion.setShortageQtySnapshot(new BigDecimal("7.0000"));
        suggestion.setExpectedArrivalDate(LocalDate.of(2026, 7, 12));
        suggestion.setStatus("DRAFT");
        suggestion.setRemark("低库存补货");
        suggestion.setDeletedFlag(0);
        suggestion.setVersion(0);
        return suggestion;
    }

    private WarehouseEntity activeWarehouse() {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCompanyId(AUDIT.companyId());
        warehouse.setAccountBookId(AUDIT.accountBookId());
        warehouse.setWarehouseName("主仓");
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private ProductEntity activeProduct() {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(AUDIT.accountBookId());
        product.setProductCode("MAT-001");
        product.setProductName("原料A");
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
    }

    private SupplierEntity activeSupplier() {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(SUPPLIER_ID);
        supplier.setCompanyId(AUDIT.companyId());
        supplier.setAccountBookId(AUDIT.accountBookId());
        supplier.setSupplierName("测试供应商");
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        return supplier;
    }

    private PurchaseOrderResponse purchaseOrder() {
        return new PurchaseOrderResponse(
                9901L,
                "PO202607060001",
                SUPPLIER_ID,
                "测试供应商",
                LocalDate.of(2026, 7, 6),
                LocalDate.of(2026, 7, 12),
                "DRAFT",
                "NOT_SUBMITTED",
                null,
                null,
                null,
                null,
                new BigDecimal("7.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "由补货建议 RS202607060001 生成。低库存补货",
                List.of()
        );
    }

    private InventoryReplenishmentSuggestionResponse response(String status) {
        return new InventoryReplenishmentSuggestionResponse(
                ID,
                "RS202607060001",
                "LOW_STOCK_ALERT",
                RULE_ID,
                WAREHOUSE_ID,
                "主仓",
                PRODUCT_ID,
                "MAT-001",
                "原料A",
                SUPPLIER_ID,
                "测试供应商",
                new BigDecimal("7.0000"),
                new BigDecimal("7.0000"),
                LocalDate.of(2026, 7, 12),
                status,
                status,
                status.equals("CONVERTED") ? 9901L : null,
                status.equals("CONVERTED") ? "PO202607060001" : null,
                "低库存补货",
                AUDIT.now()
        );
    }
}

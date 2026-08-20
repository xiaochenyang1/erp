package com.tuowei.erp.inventory.replenishment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.model.InventoryReplenishmentSuggestionEntity;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionCommandService;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionService;
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
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReplenishmentSuggestionServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9100L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 6, 9, 30)
    );
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
    private PurchaseOrderMapper purchaseOrderMapper;

    @Test
    void createFromLowStockAlertInsertsDraftSuggestionAndResolvesAlert() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(alertRule());
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(new BigDecimal("3.0000"));
        when(suggestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(suggestionMapper.insert(any(InventoryReplenishmentSuggestionEntity.class))).thenAnswer(invocation -> {
            InventoryReplenishmentSuggestionEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            return 1;
        });

        InventoryReplenishmentSuggestionResponse response = service().create(createRequest(new BigDecimal("7.0000")));

        ArgumentCaptor<InventoryReplenishmentSuggestionEntity> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionEntity.class);
        verify(suggestionMapper).insert(captor.capture());
        InventoryReplenishmentSuggestionEntity inserted = captor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getSourceType()).isEqualTo("LOW_STOCK_ALERT");
        assertThat(inserted.getSourceRuleId()).isEqualTo(RULE_ID);
        assertThat(inserted.getSuggestedQty()).isEqualByComparingTo("7.0000");
        assertThat(inserted.getShortageQtySnapshot()).isEqualByComparingTo("7.0000");
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(response.status()).isEqualTo("DRAFT");
        verify(inventoryAlertService).handle(
                WAREHOUSE_ID,
                PRODUCT_ID,
                "RESOLVED",
                "已生成补货建议 " + inserted.getSuggestionNo()
        );
    }

    @Test
    void createRejectsDuplicateDraftSuggestion() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(alertRuleMapper.selectById(RULE_ID)).thenReturn(alertRule());
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()))
                .thenReturn(new BigDecimal("3.0000"));
        when(suggestionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(draftSuggestion());

        assertThatThrownBy(() -> service().create(createRequest(new BigDecimal("7.0000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已存在待处理补货建议");
    }

    @Test
    void cancelChangesDraftSuggestionToCancelled() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(suggestionMapper.selectById(9001L)).thenReturn(draftSuggestion());
        when(suggestionMapper.updateById(any(InventoryReplenishmentSuggestionEntity.class))).thenReturn(1);

        InventoryReplenishmentSuggestionResponse response =
                service().cancel(9001L, new InventoryReplenishmentSuggestionCancelRequest("无需补货"));

        ArgumentCaptor<InventoryReplenishmentSuggestionEntity> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionEntity.class);
        verify(suggestionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
        assertThat(captor.getValue().getRemark()).contains("无需补货");
        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelRejectsConvertedSuggestion() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryReplenishmentSuggestionEntity converted = draftSuggestion();
        converted.setStatus("CONVERTED");
        when(suggestionMapper.selectById(9001L)).thenReturn(converted);

        assertThatThrownBy(() -> service().cancel(9001L, new InventoryReplenishmentSuggestionCancelRequest("无需补货")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前补货建议状态不允许取消");
    }

    @Test
    void updateChangesEditableFieldsForDraftSuggestion() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(suggestionMapper.selectById(9001L)).thenReturn(draftSuggestion());
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(suggestionMapper.updateById(any(InventoryReplenishmentSuggestionEntity.class))).thenReturn(1);

        InventoryReplenishmentSuggestionResponse response = service().update(
                9001L,
                new InventoryReplenishmentSuggestionUpdateRequest(
                        SUPPLIER_ID,
                        new BigDecimal("8.5000"),
                        LocalDate.of(2026, 7, 15),
                        "调整补货计划"
                )
        );

        ArgumentCaptor<InventoryReplenishmentSuggestionEntity> captor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionEntity.class);
        verify(suggestionMapper).updateById(captor.capture());
        InventoryReplenishmentSuggestionEntity updated = captor.getValue();
        assertThat(updated.getSupplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(updated.getSuggestedQty()).isEqualByComparingTo("8.5000");
        assertThat(updated.getExpectedArrivalDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(updated.getRemark()).isEqualTo("调整补货计划");
        assertThat(response.suggestedQty()).isEqualByComparingTo("8.5000");
    }

    @Test
    void updateRejectsConvertedSuggestion() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryReplenishmentSuggestionEntity converted = draftSuggestion();
        converted.setStatus("CONVERTED");
        when(suggestionMapper.selectById(9001L)).thenReturn(converted);

        assertThatThrownBy(() -> service().update(
                9001L,
                new InventoryReplenishmentSuggestionUpdateRequest(
                        SUPPLIER_ID,
                        new BigDecimal("8.5000"),
                        LocalDate.of(2026, 7, 15),
                        "调整补货计划"
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前补货建议状态不允许编辑");
    }

    @Test
    void convertDraftSuggestionCreatesDraftPurchaseOrder() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(suggestionMapper.selectById(9001L)).thenReturn(draftSuggestion());
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(activeSupplier());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse());
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(activeProduct());
        when(purchaseOrderService.create(any())).thenReturn(new PurchaseOrderResponse(
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
                "由补货建议 RS202607060001 生成。",
                List.of()
        ));
        when(suggestionMapper.updateById(any(InventoryReplenishmentSuggestionEntity.class))).thenReturn(1);

        InventoryReplenishmentSuggestionResponse response = service().convertToPurchaseOrder(9001L);

        ArgumentCaptor<PurchaseOrderCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(PurchaseOrderCreateRequest.class);
        verify(purchaseOrderService).create(requestCaptor.capture());
        PurchaseOrderCreateRequest request = requestCaptor.getValue();
        assertThat(request.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().get(0).productId()).isEqualTo(PRODUCT_ID);
        assertThat(request.lines().get(0).qty()).isEqualByComparingTo("7.0000");
        assertThat(request.lines().get(0).price()).isEqualByComparingTo("0");
        assertThat(request.lines().get(0).taxRate()).isEqualByComparingTo("0");

        ArgumentCaptor<InventoryReplenishmentSuggestionEntity> suggestionCaptor =
                ArgumentCaptor.forClass(InventoryReplenishmentSuggestionEntity.class);
        verify(suggestionMapper).updateById(suggestionCaptor.capture());
        assertThat(suggestionCaptor.getValue().getStatus()).isEqualTo("CONVERTED");
        assertThat(suggestionCaptor.getValue().getPurchaseOrderId()).isEqualTo(9901L);
        assertThat(suggestionCaptor.getValue().getPurchaseOrderNo()).isEqualTo("PO202607060001");
        assertThat(response.status()).isEqualTo("CONVERTED");
    }

    @Test
    void convertRejectsAlreadyConvertedSuggestion() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryReplenishmentSuggestionEntity converted = draftSuggestion();
        converted.setStatus("CONVERTED");
        when(suggestionMapper.selectById(9001L)).thenReturn(converted);

        assertThatThrownBy(() -> service().convertToPurchaseOrder(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前补货建议状态不允许转采购订单");
    }

    @Test
    void listMarksConvertedSuggestionAsReplenishedWhenPurchaseOrderReceived() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        InventoryReplenishmentSuggestionEntity converted = convertedSuggestion();
        when(suggestionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<InventoryReplenishmentSuggestionEntity> page =
                    invocation.getArgument(0);
            page.setRecords(List.of(converted));
            page.setTotal(1);
            return page;
        });
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(activeWarehouse()));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(activeProduct()));
        when(supplierMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(activeSupplier()));
        when(purchaseOrderMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(receivedPurchaseOrder()));

        InventoryReplenishmentSuggestionResponse response = service()
                .list(new com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery())
                .records()
                .get(0);

        assertThat(response.fulfillmentStatus()).isEqualTo("REPLENISHED");
    }

    private InventoryReplenishmentSuggestionService service() {
        InventoryReplenishmentSuggestionQueryService queryService =
                new InventoryReplenishmentSuggestionQueryService(
                        suggestionMapper,
                        auditMetadataFactory,
                        warehouseMapper,
                        productMapper,
                        supplierMapper,
                        purchaseOrderMapper
                );
        InventoryReplenishmentSuggestionCommandService commandService =
                new InventoryReplenishmentSuggestionCommandService(
                        suggestionMapper,
                        alertRuleMapper,
                        inventoryPostingService,
                        inventoryAlertService,
                        auditMetadataFactory,
                        warehouseMapper,
                        productMapper,
                        supplierMapper,
                        purchaseOrderService,
                        queryService
                );
        return new InventoryReplenishmentSuggestionService(
                queryService,
                commandService
        );
    }

    private InventoryReplenishmentSuggestionCreateRequest createRequest(BigDecimal suggestedQty) {
        return new InventoryReplenishmentSuggestionCreateRequest(
                RULE_ID,
                WAREHOUSE_ID,
                PRODUCT_ID,
                SUPPLIER_ID,
                suggestedQty,
                LocalDate.of(2026, 7, 12),
                "低库存补货"
        );
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
        suggestion.setId(9001L);
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

    private InventoryReplenishmentSuggestionEntity convertedSuggestion() {
        InventoryReplenishmentSuggestionEntity suggestion = draftSuggestion();
        suggestion.setStatus("CONVERTED");
        suggestion.setPurchaseOrderId(9901L);
        suggestion.setPurchaseOrderNo("PO202607060001");
        return suggestion;
    }

    private PurchaseOrderEntity receivedPurchaseOrder() {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(9901L);
        order.setCompanyId(AUDIT.companyId());
        order.setAccountBookId(AUDIT.accountBookId());
        order.setOrderNo("PO202607060001");
        order.setStatus("APPROVED");
        order.setApprovalStatus("APPROVED");
        order.setReceiptStatus("RECEIVED");
        order.setDeletedFlag(0);
        return order;
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
}

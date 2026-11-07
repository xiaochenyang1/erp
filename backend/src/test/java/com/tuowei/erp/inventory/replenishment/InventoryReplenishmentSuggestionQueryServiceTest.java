package com.tuowei.erp.inventory.replenishment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.model.InventoryReplenishmentSuggestionEntity;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionQueryService;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReplenishmentSuggestionQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9100L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 6, 9, 30)
    );
    private static final Long WAREHOUSE_ID = 8101L;
    private static final Long PRODUCT_ID = 8201L;
    private static final Long SUPPLIER_ID = 8301L;

    private final InventoryReplenishmentSuggestionMapper suggestionMapper =
            mock(InventoryReplenishmentSuggestionMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryReplenishmentSuggestionEntity.class);
    }

    @Test
    void listNormalizesAllFiltersClampsPaginationAndHydratesTenantDisplayData() {
        stubAudit();
        InventoryReplenishmentSuggestionEntity suggestion = convertedSuggestion();
        when(suggestionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryReplenishmentSuggestionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(suggestion));
            page.setTotal(1L);
            return page;
        });
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(warehouse()));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(product()));
        when(supplierMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(supplier()));
        when(purchaseOrderMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(receivedPurchaseOrder()));
        InventoryReplenishmentSuggestionPageQuery query = fullQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.suggestionNo()).isEqualTo("RS202607060001");
            assertThat(response.warehouseName()).isEqualTo("主仓");
            assertThat(response.productCode()).isEqualTo("MAT-001");
            assertThat(response.productName()).isEqualTo("原料A");
            assertThat(response.supplierName()).isEqualTo("测试供应商");
            assertThat(response.fulfillmentStatus()).isEqualTo("REPLENISHED");
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<InventoryReplenishmentSuggestionEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(suggestionMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertNormalizedQuery(queryCaptor.getValue());
    }

    @Test
    void listUsesDefaultsAndSkipsDisplayQueriesForEmptyPage() {
        stubAudit();
        when(suggestionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryReplenishmentSuggestionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        verify(warehouseMapper, never()).selectBatchIds(any(Collection.class));
        verify(productMapper, never()).selectBatchIds(any(Collection.class));
        verify(supplierMapper, never()).selectBatchIds(any(Collection.class));
        verify(purchaseOrderMapper, never()).selectBatchIds(any(Collection.class));
    }

    @Test
    void listDropsCrossTenantDisplayRows() {
        stubAudit();
        when(suggestionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryReplenishmentSuggestionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(convertedSuggestion()));
            page.setTotal(1L);
            return page;
        });
        WarehouseEntity crossTenantWarehouse = warehouse();
        crossTenantWarehouse.setAccountBookId(99L);
        when(warehouseMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(crossTenantWarehouse));
        when(productMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(product()));
        when(supplierMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(supplier()));
        when(purchaseOrderMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(receivedPurchaseOrder()));

        var result = service().list(new InventoryReplenishmentSuggestionPageQuery());

        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.warehouseName()).isNull();
            assertThat(response.productName()).isEqualTo("原料A");
        });
    }

    @Test
    void requireSuggestionRejectsCrossTenantRecord() {
        stubAudit();
        InventoryReplenishmentSuggestionEntity suggestion = convertedSuggestion();
        suggestion.setCompanyId(99L);
        when(suggestionMapper.selectById(suggestion.getId())).thenReturn(suggestion);

        assertThatThrownBy(() -> service().requireSuggestion(suggestion.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("补货建议不存在");
    }

    @Test
    void toResponseResolvesAllFulfillmentStates() {
        InventoryReplenishmentSuggestionEntity suggestion = convertedSuggestion();
        PurchaseOrderEntity order = receivedPurchaseOrder();
        assertThat(service().toResponse(suggestion, warehouse(), product(), supplier(), order).fulfillmentStatus())
                .isEqualTo("REPLENISHED");

        order.setReceiptStatus("PARTIAL_RECEIVED");
        assertThat(service().toResponse(suggestion, warehouse(), product(), supplier(), order).fulfillmentStatus())
                .isEqualTo("PARTIAL_RECEIVED");

        order.setReceiptStatus("NOT_RECEIVED");
        order.setStatus("CLOSED");
        assertThat(service().toResponse(suggestion, warehouse(), product(), supplier(), order).fulfillmentStatus())
                .isEqualTo("PURCHASE_CLOSED");

        suggestion.setStatus("DRAFT");
        assertThat(service().toResponse(suggestion, warehouse(), product(), supplier(), null).fulfillmentStatus())
                .isEqualTo("SUGGESTED");
        suggestion.setStatus("CANCELLED");
        assertThat(service().toResponse(suggestion, warehouse(), product(), supplier(), null).fulfillmentStatus())
                .isEqualTo("CANCELLED");
    }

    private InventoryReplenishmentSuggestionQueryService service() {
        return new InventoryReplenishmentSuggestionQueryService(
                suggestionMapper,
                auditMetadataFactory,
                warehouseMapper,
                productMapper,
                supplierMapper,
                purchaseOrderMapper
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private InventoryReplenishmentSuggestionPageQuery fullQuery() {
        InventoryReplenishmentSuggestionPageQuery query = new InventoryReplenishmentSuggestionPageQuery();
        query.setSuggestionNo("  RS202607  ");
        query.setStatus("  converted  ");
        query.setWarehouseId(WAREHOUSE_ID);
        query.setProductId(PRODUCT_ID);
        query.setSupplierId(SUPPLIER_ID);
        query.setCreatedTimeFrom(LocalDateTime.of(2026, 7, 1, 0, 0));
        query.setCreatedTimeTo(LocalDateTime.of(2026, 7, 31, 23, 59));
        return query;
    }

    private void assertNormalizedQuery(LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "company_id",
                        "account_book_id",
                        "deleted_flag",
                        "suggestion_no",
                        "status",
                        "warehouse_id",
                        "product_id",
                        "supplier_id",
                        "created_time"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).contains(
                AUDIT.companyId(),
                AUDIT.accountBookId(),
                "%RS202607%",
                "CONVERTED",
                WAREHOUSE_ID,
                PRODUCT_ID,
                SUPPLIER_ID,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 31, 23, 59)
        );
    }

    private InventoryReplenishmentSuggestionEntity convertedSuggestion() {
        InventoryReplenishmentSuggestionEntity suggestion = new InventoryReplenishmentSuggestionEntity();
        suggestion.setId(9001L);
        suggestion.setCompanyId(AUDIT.companyId());
        suggestion.setAccountBookId(AUDIT.accountBookId());
        suggestion.setSuggestionNo("RS202607060001");
        suggestion.setSourceType("LOW_STOCK_ALERT");
        suggestion.setSourceRuleId(7101L);
        suggestion.setWarehouseId(WAREHOUSE_ID);
        suggestion.setProductId(PRODUCT_ID);
        suggestion.setSupplierId(SUPPLIER_ID);
        suggestion.setSuggestedQty(new BigDecimal("7.0000"));
        suggestion.setShortageQtySnapshot(new BigDecimal("7.0000"));
        suggestion.setExpectedArrivalDate(LocalDate.of(2026, 7, 12));
        suggestion.setStatus("CONVERTED");
        suggestion.setPurchaseOrderId(9901L);
        suggestion.setPurchaseOrderNo("PO202607060001");
        suggestion.setRemark("低库存补货");
        suggestion.setCreatedTime(AUDIT.now());
        suggestion.setDeletedFlag(0);
        return suggestion;
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
        ProductEntity entity = new ProductEntity();
        entity.setId(PRODUCT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setProductCode("MAT-001");
        entity.setProductName("原料A");
        return entity;
    }

    private SupplierEntity supplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(SUPPLIER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setSupplierName("测试供应商");
        return entity;
    }

    private PurchaseOrderEntity receivedPurchaseOrder() {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(9901L);
        order.setCompanyId(AUDIT.companyId());
        order.setAccountBookId(AUDIT.accountBookId());
        order.setStatus("APPROVED");
        order.setReceiptStatus("RECEIVED");
        return order;
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

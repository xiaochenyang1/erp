package com.tuowei.erp.inventory.mrp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.model.MrpRunEntity;
import com.tuowei.erp.inventory.mrp.model.MrpRunLineEntity;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCommandService;
import com.tuowei.erp.inventory.mrp.service.MrpPlanQueryService;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MrpPlanCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 20, 11, 30)
    );
    private static final Long RUN_ID = 8101L;
    private static final Long LINE_ID = 8201L;
    private static final Long PRODUCT_ID = 8301L;
    private static final Long BOM_ID = 8401L;
    private static final Long SUPPLIER_ID = 8501L;
    private static final Long WAREHOUSE_ID = 8601L;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private MrpRunMapper mrpRunMapper;
    @Mock
    private MrpRunLineMapper mrpRunLineMapper;
    @Mock
    private SequenceNumberGenerator sequenceNumberGenerator;
    @Mock
    private PurchaseOrderService purchaseOrderService;
    @Mock
    private ProductionOrderService productionOrderService;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private MrpPlanQueryService mrpPlanQueryService;
    @Mock
    private MrpPlanCalculationService mrpPlanCalculationService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(MrpRunEntity.class);
        initTableInfo(MrpRunLineEntity.class);
        initTableInfo(SupplierEntity.class);
        initTableInfo(WarehouseEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void runPersistsHeaderThenProductionAndPurchaseLinesAndReloadsResponse() {
        MrpPlanCalculationService.ComputedLine production = new MrpPlanCalculationService.ComputedLine(
                8302L, "PRODUCTION", new BigDecimal("12.00009"), new BigDecimal("2"),
                new BigDecimal("1"), new BigDecimal("9.00009"), BOM_ID, "销售未发货;有BOM建议生产");
        MrpPlanCalculationService.ComputedLine purchase = new MrpPlanCalculationService.ComputedLine(
                PRODUCT_ID, "PURCHASE", new BigDecimal("8.00009"), new BigDecimal("1"),
                new BigDecimal("2"), new BigDecimal("5.00009"), null, "无BOM建议采购");
        when(mrpPlanCalculationService.calculate(AUDIT)).thenReturn(
                new MrpPlanCalculationService.CalculationResult(List.of(production), List.of(purchase))
        );
        when(sequenceNumberGenerator.nextNumber("MRP_RUN", "MRP计划", AUDIT.now().toLocalDate()))
                .thenReturn("MRP202608200001");
        when(mrpRunMapper.insert(any(MrpRunEntity.class))).thenAnswer(invocation -> {
            MrpRunEntity run = invocation.getArgument(0);
            run.setId(RUN_ID);
            return 1;
        });
        when(mrpPlanQueryService.getById(RUN_ID)).thenReturn(null);

        assertThat(service().run()).isNull();

        ArgumentCaptor<MrpRunEntity> runCaptor = ArgumentCaptor.forClass(MrpRunEntity.class);
        verify(mrpRunMapper).insert(runCaptor.capture());
        MrpRunEntity run = runCaptor.getValue();
        assertThat(run.getId()).isEqualTo(RUN_ID);
        assertThat(run.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(run.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(run.getRunNo()).isEqualTo("MRP202608200001");
        assertThat(run.getAsOfDate()).isEqualTo(AUDIT.now().toLocalDate());
        assertThat(run.getStatus()).isEqualTo("OPEN");
        assertThat(run.getPurchaseCount()).isEqualTo(1);
        assertThat(run.getProductionCount()).isEqualTo(1);
        assertThat(run.getVersion()).isZero();

        ArgumentCaptor<MrpRunLineEntity> lineCaptor = ArgumentCaptor.forClass(MrpRunLineEntity.class);
        verify(mrpRunLineMapper, org.mockito.Mockito.times(2)).insert(lineCaptor.capture());
        List<MrpRunLineEntity> lines = lineCaptor.getAllValues();
        assertThat(lines).extracting(MrpRunLineEntity::getLineNo).containsExactly(1, 2);
        assertThat(lines).extracting(MrpRunLineEntity::getSuggestionType)
                .containsExactly("PRODUCTION", "PURCHASE");
        assertThat(lines.get(0).getNetQty()).isEqualByComparingTo("9.0001");
        assertThat(lines.get(1).getNetQty()).isEqualByComparingTo("5.0001");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getRunId()).isEqualTo(RUN_ID);
            assertThat(line.getCompanyId()).isEqualTo(AUDIT.companyId());
            assertThat(line.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
            assertThat(line.getStatus()).isEqualTo("OPEN");
            assertThat(line.getDeletedFlag()).isZero();
            assertThat(line.getVersion()).isZero();
        });

        InOrder order = inOrder(mrpPlanCalculationService, sequenceNumberGenerator, mrpRunMapper,
                mrpRunLineMapper, mrpPlanQueryService);
        order.verify(mrpPlanCalculationService).calculate(AUDIT);
        order.verify(sequenceNumberGenerator).nextNumber("MRP_RUN", "MRP计划", AUDIT.now().toLocalDate());
        order.verify(mrpRunMapper).insert(any(MrpRunEntity.class));
        order.verify(mrpRunLineMapper, org.mockito.Mockito.times(2)).insert(any(MrpRunLineEntity.class));
        order.verify(mrpPlanQueryService).getById(RUN_ID);
    }

    @Test
    void convertPurchaseUsesExplicitSupplierProductPricingAndUpdatesLine() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PURCHASE");
        ProductEntity product = product();
        product.setPurchasePrice(new BigDecimal("12.34567"));
        product.setTaxRate(new BigDecimal("13"));
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        MrpSuggestionLineResponse response = org.mockito.Mockito.mock(MrpSuggestionLineResponse.class);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(response);

        assertThat(service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        )).isSameAs(response);

        ArgumentCaptor<com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest> orderCaptor =
                ArgumentCaptor.forClass(com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest.class);
        verify(purchaseOrderService).create(orderCaptor.capture());
        var request = orderCaptor.getValue();
        assertThat(request.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(request.orderDate()).isEqualTo(AUDIT.now().toLocalDate());
        assertThat(request.deliveryDate()).isEqualTo(AUDIT.now().toLocalDate().plusDays(7));
        assertThat(request.remark()).isEqualTo("由MRP计划行生成");
        assertThat(request.lines()).singleElement().satisfies(orderLine -> {
            assertThat(orderLine.productId()).isEqualTo(PRODUCT_ID);
            assertThat(orderLine.qty()).isEqualByComparingTo(line.getNetQty());
            assertThat(orderLine.price()).isEqualByComparingTo("12.35");
            assertThat(orderLine.taxRate()).isEqualByComparingTo("0.13");
        });
        assertThat(line.getStatus()).isEqualTo("CONVERTED");
        assertThat(line.getConvertedBizType()).isEqualTo("PURCHASE_ORDER");
        assertThat(line.getConvertedBizId()).isEqualTo(9901L);
        verify(mrpRunMapper, never()).updateById(any(MrpRunEntity.class));
    }

    @Test
    void convertPurchaseFallsBackToFirstActiveTenantSupplierWhenRequestIsNull() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PURCHASE");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectOne(any())).thenReturn(supplier(SUPPLIER_ID));
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(RUN_ID, LINE_ID, null);

        ArgumentCaptor<com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest.class);
        verify(purchaseOrderService).create(captor.capture());
        assertThat(captor.getValue().supplierId()).isEqualTo(SUPPLIER_ID);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierEntity>> wrapper =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(supplierMapper).selectOne(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase()).contains("company_id", "account_book_id", "deleted_flag", "status");
    }

    @Test
    void convertProductionFallsBackToOneActiveTenantWarehouseForBothRoles() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PRODUCTION");
        WarehouseEntity warehouse = warehouse(WAREHOUSE_ID);
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(warehouseMapper.selectOne(any())).thenReturn(warehouse);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(productionOrderService.create(any())).thenReturn(productionOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(RUN_ID, LINE_ID, new MrpConvertLineRequest(null, null, null));

        ArgumentCaptor<com.tuowei.erp.production.order.web.ProductionOrderCreateRequest> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.production.order.web.ProductionOrderCreateRequest.class);
        verify(productionOrderService).create(captor.capture());
        assertThat(captor.getValue().bomId()).isEqualTo(BOM_ID);
        assertThat(captor.getValue().finishedWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(captor.getValue().materialWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(line.getStatus()).isEqualTo("CONVERTED");
    }

    @Test
    void convertRejectsNonOpenRunBeforeLoadingLine() {
        MrpRunEntity run = openRun();
        run.setStatus("CLOSED");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前MRP计划状态不允许转单");
        verify(mrpPlanQueryService, never()).requireLine(anyLong(), anyLong(), eq(AUDIT));
        verify(purchaseOrderService, never()).create(any());
        verify(productionOrderService, never()).create(any());
    }

    @Test
    void convertRejectsNonOpenLineBeforeReadingConversionDependencies() {
        MrpRunLineEntity line = openLine("PURCHASE");
        line.setStatus("CONVERTED");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前建议行状态不允许转单");
        verify(supplierMapper, never()).selectOne(any());
        verify(purchaseOrderService, never()).create(any());
        verify(productionOrderService, never()).create(any());
    }

    @Test
    void convertRejectsUnknownSuggestionTypeBeforeWriting() {
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("TRANSFER"));

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未知建议类型: TRANSFER");
        verify(mrpRunLineMapper, never()).updateById(any(MrpRunLineEntity.class));
    }

    @Test
    void convertPurchaseRejectsMissingDefaultSupplierBeforeProductLookup() {
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("PURCHASE"));
        when(supplierMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择供应商后再转采购订单");
        verify(supplierMapper, never()).selectById(any());
        verify(productMapper, never()).selectById(any());
        verify(purchaseOrderService, never()).create(any());
    }

    @Test
    void convertPurchaseRejectsSupplierFromAnotherAccountBook() {
        SupplierEntity supplier = supplier(SUPPLIER_ID);
        supplier.setAccountBookId(999L);
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("PURCHASE"));
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier);

        assertThatThrownBy(() -> service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或未启用");
        verify(productMapper, never()).selectById(any());
        verify(purchaseOrderService, never()).create(any());
    }

    @Test
    void convertPurchaseRejectsProductFromAnotherTenant() {
        ProductEntity product = product();
        product.setCompanyId(999L);
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("PURCHASE"));
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);

        assertThatThrownBy(() -> service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在");
        verify(purchaseOrderService, never()).create(any());
    }

    @Test
    void convertPurchaseKeepsDecimalTaxRateAndDoesNotRequireActiveProductStatus() {
        MrpRunLineEntity line = openLine("PURCHASE");
        ProductEntity product = product();
        product.setStatus("INACTIVE");
        product.setTaxRate(new BigDecimal("0.13"));
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        );

        ArgumentCaptor<com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest.class);
        verify(purchaseOrderService).create(captor.capture());
        assertThat(captor.getValue().lines()).singleElement()
                .satisfies(orderLine -> assertThat(orderLine.taxRate()).isEqualByComparingTo("0.13"));
    }

    @Test
    void convertProductionUsesExplicitWarehousesAndValidatesMaterialBeforeFinished() {
        Long finishedWarehouseId = 8602L;
        Long materialWarehouseId = 8603L;
        MrpRunLineEntity line = openLine("PRODUCTION");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(warehouseMapper.selectById(materialWarehouseId)).thenReturn(warehouse(materialWarehouseId));
        when(warehouseMapper.selectById(finishedWarehouseId)).thenReturn(warehouse(finishedWarehouseId));
        when(productionOrderService.create(any())).thenReturn(productionOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(
                RUN_ID,
                LINE_ID,
                new MrpConvertLineRequest(null, finishedWarehouseId, materialWarehouseId)
        );

        ArgumentCaptor<com.tuowei.erp.production.order.web.ProductionOrderCreateRequest> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.production.order.web.ProductionOrderCreateRequest.class);
        verify(productionOrderService).create(captor.capture());
        assertThat(captor.getValue().finishedWarehouseId()).isEqualTo(finishedWarehouseId);
        assertThat(captor.getValue().materialWarehouseId()).isEqualTo(materialWarehouseId);
        assertThat(captor.getValue().plannedQty()).isEqualByComparingTo(line.getNetQty());
        assertThat(captor.getValue().plannedStartDate()).isEqualTo(AUDIT.now().toLocalDate());
        assertThat(captor.getValue().plannedFinishDate()).isEqualTo(AUDIT.now().toLocalDate().plusDays(7));
        InOrder warehouseOrder = inOrder(warehouseMapper, productionOrderService);
        warehouseOrder.verify(warehouseMapper).selectById(materialWarehouseId);
        warehouseOrder.verify(warehouseMapper).selectById(finishedWarehouseId);
        warehouseOrder.verify(productionOrderService).create(any());
    }

    @Test
    void convertProductionUsesDefaultOnlyForMissingWarehouse() {
        Long explicitFinishedWarehouseId = 8602L;
        MrpRunLineEntity line = openLine("PRODUCTION");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(warehouseMapper.selectOne(any())).thenReturn(warehouse(WAREHOUSE_ID));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(WAREHOUSE_ID));
        when(warehouseMapper.selectById(explicitFinishedWarehouseId))
                .thenReturn(warehouse(explicitFinishedWarehouseId));
        when(productionOrderService.create(any())).thenReturn(productionOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(1L);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(
                RUN_ID,
                LINE_ID,
                new MrpConvertLineRequest(null, explicitFinishedWarehouseId, null)
        );

        ArgumentCaptor<com.tuowei.erp.production.order.web.ProductionOrderCreateRequest> captor =
                ArgumentCaptor.forClass(com.tuowei.erp.production.order.web.ProductionOrderCreateRequest.class);
        verify(productionOrderService).create(captor.capture());
        assertThat(captor.getValue().finishedWarehouseId()).isEqualTo(explicitFinishedWarehouseId);
        assertThat(captor.getValue().materialWarehouseId()).isEqualTo(WAREHOUSE_ID);
    }

    @Test
    void convertProductionRejectsMissingBomBeforeWarehouseLookup() {
        MrpRunLineEntity line = openLine("PRODUCTION");
        line.setBomId(null);
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产建议缺少BOM，无法转生产订单");
        verify(warehouseMapper, never()).selectOne(any());
        verify(warehouseMapper, never()).selectById(any());
        verify(productionOrderService, never()).create(any());
    }

    @Test
    void convertProductionRejectsMissingDefaultWarehouse() {
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("PRODUCTION"));
        when(warehouseMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().convertLine(RUN_ID, LINE_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择材料仓和成品仓后再转生产订单");
        verify(warehouseMapper, never()).selectById(any());
        verify(productionOrderService, never()).create(any());
    }

    @Test
    void convertProductionRejectsCrossTenantMaterialWarehouseBeforeFinishedWarehouse() {
        Long finishedWarehouseId = 8602L;
        WarehouseEntity materialWarehouse = warehouse(WAREHOUSE_ID);
        materialWarehouse.setCompanyId(999L);
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(openRun());
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(openLine("PRODUCTION"));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(materialWarehouse);

        assertThatThrownBy(() -> service().convertLine(
                RUN_ID,
                LINE_ID,
                new MrpConvertLineRequest(null, finishedWarehouseId, WAREHOUSE_ID)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或未启用");
        verify(warehouseMapper, never()).selectById(finishedWarehouseId);
        verify(productionOrderService, never()).create(any());
    }

    @Test
    void convertReturnsConflictAndDoesNotRefreshRunWhenLineOptimisticLockFails() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PURCHASE");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(0);

        assertThatThrownBy(() -> service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        )).isInstanceOf(BusinessConflictException.class)
                .hasMessage("MRP建议行已被其他操作修改，请刷新后重试");
        verify(mrpRunLineMapper, never()).selectCount(any());
        verify(mrpRunMapper, never()).updateById(any(MrpRunEntity.class));
    }

    @Test
    void convertLastOpenLineClosesRunWithOptimisticLock() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PURCHASE");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(0L);
        when(mrpRunMapper.updateById(run)).thenReturn(1);
        when(mrpPlanQueryService.toLineResponse(line, AUDIT)).thenReturn(null);

        service().convertLine(RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null));

        assertThat(run.getStatus()).isEqualTo("CLOSED");
        assertThat(run.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(run.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(mrpRunMapper).updateById(run);
    }

    @Test
    void convertLastOpenLineReturnsConflictWhenRunOptimisticLockFails() {
        MrpRunEntity run = openRun();
        MrpRunLineEntity line = openLine("PURCHASE");
        when(mrpPlanQueryService.requireRun(RUN_ID, AUDIT)).thenReturn(run);
        when(mrpPlanQueryService.requireLine(RUN_ID, LINE_ID, AUDIT)).thenReturn(line);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier(SUPPLIER_ID));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(purchaseOrderService.create(any())).thenReturn(purchaseOrder());
        when(mrpRunLineMapper.updateById(line)).thenReturn(1);
        when(mrpRunLineMapper.selectCount(any())).thenReturn(0L);
        when(mrpRunMapper.updateById(run)).thenReturn(0);

        assertThatThrownBy(() -> service().convertLine(
                RUN_ID, LINE_ID, new MrpConvertLineRequest(SUPPLIER_ID, null, null)
        )).isInstanceOf(BusinessConflictException.class)
                .hasMessage("MRP计划已被其他操作修改，请刷新后重试");
        verify(mrpPlanQueryService, never()).toLineResponse(any(MrpRunLineEntity.class), eq(AUDIT));
    }

    private MrpPlanCommandService service() {
        return new MrpPlanCommandService(
                auditMetadataFactory,
                mrpRunMapper,
                mrpRunLineMapper,
                sequenceNumberGenerator,
                purchaseOrderService,
                productionOrderService,
                productMapper,
                supplierMapper,
                warehouseMapper,
                mrpPlanQueryService,
                mrpPlanCalculationService
        );
    }

    private MrpRunEntity openRun() {
        MrpRunEntity run = new MrpRunEntity();
        run.setId(RUN_ID);
        run.setCompanyId(AUDIT.companyId());
        run.setAccountBookId(AUDIT.accountBookId());
        run.setStatus("OPEN");
        run.setVersion(0);
        return run;
    }

    private MrpRunLineEntity openLine(String type) {
        MrpRunLineEntity line = new MrpRunLineEntity();
        line.setId(LINE_ID);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setRunId(RUN_ID);
        line.setLineNo(1);
        line.setProductId(PRODUCT_ID);
        line.setSuggestionType(type);
        line.setNetQty(new BigDecimal("5.0000"));
        line.setBomId("PRODUCTION".equals(type) ? BOM_ID : null);
        line.setStatus("OPEN");
        line.setDeletedFlag(0);
        line.setVersion(0);
        return line;
    }

    private ProductEntity product() {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(AUDIT.accountBookId());
        product.setPurchasePrice(new BigDecimal("2.0000"));
        product.setTaxRate(new BigDecimal("0.13"));
        product.setDeletedFlag(0);
        return product;
    }

    private SupplierEntity supplier(Long id) {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(id);
        supplier.setCompanyId(AUDIT.companyId());
        supplier.setAccountBookId(AUDIT.accountBookId());
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        return supplier;
    }

    private WarehouseEntity warehouse(Long id) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(AUDIT.companyId());
        warehouse.setAccountBookId(AUDIT.accountBookId());
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private PurchaseOrderResponse purchaseOrder() {
        return new PurchaseOrderResponse(
                9901L, "PO202608200001", SUPPLIER_ID, null,
                AUDIT.now().toLocalDate(), AUDIT.now().toLocalDate().plusDays(7),
                "DRAFT", null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "MRP", List.of()
        );
    }

    private ProductionOrderResponse productionOrder() {
        return new ProductionOrderResponse(
                9951L, "MO202608200001", BOM_ID, PRODUCT_ID, WAREHOUSE_ID, WAREHOUSE_ID,
                new BigDecimal("5.0000"), BigDecimal.ZERO,
                AUDIT.now().toLocalDate(), AUDIT.now().toLocalDate().plusDays(7),
                "DRAFT", BigDecimal.ZERO, BigDecimal.ZERO, "MRP", List.of()
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityClass.getName()
        );
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

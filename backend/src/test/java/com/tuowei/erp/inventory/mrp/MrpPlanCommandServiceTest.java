package com.tuowei.erp.inventory.mrp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.*;
import com.tuowei.erp.inventory.mrp.mapper.*;
import com.tuowei.erp.inventory.mrp.model.*;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot.*;
import com.tuowei.erp.inventory.mrp.service.*;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService.*;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MrpPlanCommandServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(9001L, 101L, 202L, LocalDateTime.of(2026,11,6,11,30));
    private final AuditMetadataFactory audit = mock(AuditMetadataFactory.class);
    private final MrpRunMapper runs = mock(MrpRunMapper.class);
    private final MrpRunLineMapper lines = mock(MrpRunLineMapper.class);
    private final SequenceNumberGenerator numbers = mock(SequenceNumberGenerator.class);
    private final PurchaseOrderService purchase = mock(PurchaseOrderService.class);
    private final ProductionOrderService production = mock(ProductionOrderService.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final SupplierMapper suppliers = mock(SupplierMapper.class);
    private final WarehouseMapper warehouses = mock(WarehouseMapper.class);
    private final MrpPlanQueryService query = mock(MrpPlanQueryService.class);
    private final MrpPlanCalculationService calculation = mock(MrpPlanCalculationService.class);
    private final MrpAccessPolicy access = mock(MrpAccessPolicy.class);
    private final MrpSnapshotCodec codec = new MrpSnapshotCodec(new ObjectMapper());
    private final MrpPlanCommandService service = new MrpPlanCommandService(audit,runs,lines,numbers,purchase,production,products,suppliers,warehouses,query,calculation,codec,access);
    private MrpRunEntity run;
    private MrpRunLineEntity line;
    private Node node;

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"mrp"),MrpRunEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"mrp"),MrpRunLineEntity.class);
    }
    @BeforeEach void setup() {
        when(audit.current()).thenReturn(AUDIT);
        run = new MrpRunEntity(); run.setId(81L); run.setCompanyId(101L); run.setAccountBookId(202L); run.setStatus("OPEN"); run.setAlgorithmVersion(2);
        line = new MrpRunLineEntity(); line.setId(82L); line.setRunId(81L); line.setCompanyId(101L); line.setAccountBookId(202L); line.setLineNo(1); line.setStatus("OPEN");
        when(query.requireRun(81L,AUDIT)).thenReturn(run); when(query.requireLine(81L,82L,AUDIT)).thenReturn(line);
        when(lines.updateById(any(MrpRunLineEntity.class))).thenReturn(1); when(lines.selectCount(any())).thenReturn(1L);
        when(runs.updateById(any(MrpRunEntity.class))).thenReturn(1);
        setPlan(false);
        ProductEntity product = new ProductEntity(); product.setId(83L); product.setCompanyId(101L); product.setAccountBookId(202L); product.setDeletedFlag(0);
        product.setPurchasePrice(bd("12.30")); product.setTaxRate(bd("13")); when(products.selectById(83L)).thenReturn(product);
        SupplierEntity supplier = new SupplierEntity(); supplier.setId(85L); supplier.setCompanyId(101L); supplier.setAccountBookId(202L); supplier.setDeletedFlag(0); supplier.setStatus("ACTIVE");
        when(suppliers.selectById(85L)).thenReturn(supplier);
        for (long id : List.of(86L,87L)) {
            WarehouseEntity w = new WarehouseEntity(); w.setId(id); w.setCompanyId(101L); w.setAccountBookId(202L); w.setDeletedFlag(0); w.setStatus("ACTIVE");
            when(warehouses.selectById(id)).thenReturn(w);
        }
        PurchaseOrderResponse po = mock(PurchaseOrderResponse.class); when(po.id()).thenReturn(91L); when(po.orderNo()).thenReturn("PO-91"); when(purchase.create(any())).thenReturn(po);
        ProductionOrderResponse mo = mock(ProductionOrderResponse.class); when(mo.id()).thenReturn(92L); when(mo.orderNo()).thenReturn("MO-92"); when(production.create(any())).thenReturn(mo);
    }

    @Test void savesWarehouseBomAndFullCalculationSnapshot() {
        setPlan(true);
        when(numbers.nextNumber(anyString(),anyString(),any())).thenReturn("MRP-1");
        when(runs.insert(any(MrpRunEntity.class))).thenAnswer(inv -> { inv.getArgument(0,MrpRunEntity.class).setId(99L); return 1; });
        service.run();
        var runCaptor = ArgumentCaptor.forClass(MrpRunEntity.class); verify(runs).insert(runCaptor.capture());
        assertThat(runCaptor.getValue().getAlgorithmVersion()).isEqualTo(2);
        assertThat(codec.snapshot(runCaptor.getValue().getCalculationSnapshot()).nodes()).containsExactly(node);
        var captor = ArgumentCaptor.forClass(MrpRunLineEntity.class); verify(lines).insert(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(86L);
        assertThat(captor.getValue().getMaterialWarehouseId()).isEqualTo(87L);
        assertThat(captor.getValue().getBomVersion()).isEqualTo(7);
        assertThat(codec.node(captor.getValue().getCalculationTrace())).isEqualTo(node);
        var order = inOrder(calculation,runs); order.verify(calculation).lockPlanning(AUDIT); order.verify(calculation).calculate(AUDIT); order.verify(runs).insert(any(MrpRunEntity.class));
    }

    @Test void emptyPlanIsClosedAndCalculationFailureDoesNotPersist() {
        when(calculation.calculate(AUDIT)).thenReturn(new CalculationResult(List.of(),List.of(),new MrpCalculationSnapshot(2,List.of(),List.of())));
        service.run();
        var captor = ArgumentCaptor.forClass(MrpRunEntity.class); verify(runs).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED"); verify(lines,never()).insert(any(MrpRunLineEntity.class));
        reset(runs); when(calculation.calculate(AUDIT)).thenThrow(new IllegalArgumentException("循环BOM"));
        assertThatThrownBy(service::run).hasMessageContaining("循环BOM"); verifyNoInteractions(runs);
    }

    @Test void purchaseConversionUsesExplicitSupplierAndCalculatedReceivingWarehouse() {
        convert();
        var captor = ArgumentCaptor.forClass(PurchaseOrderCreateRequest.class); verify(purchase).create(captor.capture());
        assertThat(captor.getValue().supplierId()).isEqualTo(85L); assertThat(captor.getValue().plannedWarehouseId()).isEqualTo(86L);
        assertThat(captor.getValue().lines().get(0).qty()).isEqualByComparingTo("10");
        assertThat(captor.getValue().lines().get(0).taxRate()).isEqualByComparingTo("0.13");
        assertThat(line.getConvertedBizNo()).isEqualTo("PO-91"); assertThat(line.getStatus()).isEqualTo("CONVERTED");
        verify(access).requireConversion("PURCHASE");
        var order = inOrder(calculation,purchase,lines); order.verify(calculation).lockPlanning(AUDIT); order.verify(calculation).calculate(AUDIT); order.verify(purchase).create(any()); order.verify(lines).updateById(line);
    }

    @Test void productionConversionUsesSnapshotBomAndBothWarehousesWithoutFallback() {
        setPlan(true); service.convertLine(81L,82L,null);
        var captor = ArgumentCaptor.forClass(ProductionOrderCreateRequest.class); verify(production).create(captor.capture());
        assertThat(captor.getValue().bomId()).isEqualTo(84L); assertThat(captor.getValue().finishedWarehouseId()).isEqualTo(86L);
        assertThat(captor.getValue().materialWarehouseId()).isEqualTo(87L); assertThat(captor.getValue().plannedQty()).isEqualByComparingTo("10");
        verify(access).requireConversion("PRODUCTION");
    }

    @Test void refusesWarehouseChangesDuringConversion() {
        setPlan(true);
        assertThatThrownBy(() -> service.convertLine(81L,82L,new MrpConvertLineRequest(null,86L,86L))).hasMessageContaining("计算仓库一致");
        verifyNoInteractions(production);
    }

    @Test void requiresSupplierAndValidTenantReferences() {
        assertThatThrownBy(() -> service.convertLine(81L,82L,null)).hasMessageContaining("请选择供应商");
        when(suppliers.selectById(85L)).thenReturn(null);
        assertThatThrownBy(this::convert).hasMessageContaining("供应商不存在"); verifyNoInteractions(purchase);
    }

    @Test void refusesForeignProductOrDisabledWarehouse() {
        when(products.selectById(83L)).thenReturn(new ProductEntity());
        assertThatThrownBy(this::convert).hasMessageContaining("商品不存在");
        when(warehouses.selectById(86L)).thenReturn(null);
        assertThatThrownBy(this::convert).hasMessageContaining("仓库不存在"); verifyNoInteractions(purchase);
    }

    @Test void rejectsLegacyPlansRatherThanInventingWarehouseAssignments() {
        run.setAlgorithmVersion(1); run.setCalculationSnapshot(null); line.setCalculationTrace(null);
        assertThatThrownBy(this::convert).isInstanceOf(BusinessConflictException.class).hasMessageContaining("历史MRP");
        verifyNoInteractions(purchase,production);
    }

    @Test void rejectsWarningsInSavedOrFreshInputs() {
        var warnings = List.of(new Warning("PURCHASE_WAREHOUSE_REQUIRED",91L,"PO-91",83L,bd("1"),"补仓"));
        run.setCalculationSnapshot(codec.write(new MrpCalculationSnapshot(2,List.of(node),warnings)));
        assertThatThrownBy(this::convert).hasMessageContaining("预计收货仓库");
        run.setCalculationSnapshot(codec.write(new MrpCalculationSnapshot(2,List.of(node),List.of())));
        when(calculation.calculate(AUDIT)).thenReturn(new CalculationResult(List.of(),List.of(),new MrpCalculationSnapshot(2,List.of(node),warnings)));
        assertThatThrownBy(this::convert).hasMessageContaining("预计收货仓库"); verifyNoInteractions(purchase);
    }

    @Test void rejectsOldShortagesAfterAnotherPlanReservedSupply() {
        when(calculation.calculate(AUDIT)).thenReturn(new CalculationResult(List.of(),List.of(),new MrpCalculationSnapshot(2,List.of(),List.of())));
        assertThatThrownBy(this::convert).hasMessageContaining("已变化"); verifyNoInteractions(purchase,production);
    }

    @Test void refusesChangedQuantityOrBomEvenIfAnotherRunHasTheSameProduct() {
        line.setNetQty(bd("11")); assertThatThrownBy(this::convert).hasMessageContaining("已变化");
        setPlan(true); line.setBomVersion(6); assertThatThrownBy(this::convert).hasMessageContaining("已变化");
        verifyNoInteractions(purchase,production);
    }

    @Test void checksTargetCreatePermissionBeforeRecalculationOrWrites() {
        doThrow(new AccessDeniedException("缺少创建权限")).when(access).requireConversion("PURCHASE");
        assertThatThrownBy(this::convert).isInstanceOf(AccessDeniedException.class);
        verify(calculation,never()).calculate(AUDIT); verifyNoInteractions(purchase,production);
    }

    @Test void rejectsRepeatedConversionAndClosesFullyConvertedRun() {
        when(lines.selectCount(any())).thenReturn(0L); convert();
        assertThat(run.getStatus()).isEqualTo("CLOSED");
        assertThatThrownBy(this::convert).hasMessageContaining("计划状态不允许");
        verify(purchase,times(1)).create(any());
    }

    @Test void rejectsAlreadyConvertedLineInOpenRun() {
        line.setStatus("CONVERTED"); assertThatThrownBy(this::convert).hasMessageContaining("建议行状态不允许");
        verifyNoInteractions(purchase,production);
    }

    @Test void optimisticWriteFailurePropagatesToRollBackTransaction() {
        when(lines.updateById(any(MrpRunLineEntity.class))).thenReturn(0);
        assertThatThrownBy(this::convert).isInstanceOf(BusinessConflictException.class);
        verify(purchase).create(any()); verify(runs,never()).updateById(any(MrpRunEntity.class));
    }

    private void convert() { service.convertLine(81L,82L,new MrpConvertLineRequest(85L,null,null)); }
    private void setPlan(boolean make) {
        node = new Node(83L,"P83","商品",86L,"成品仓",make?"PRODUCTION":"PURCHASE",make?87L:null,make?"材料仓":null,
                make?84L:null,make?"BOM84":null,make?7:null,1,bd("12"),bd("1"),bd("1"),bd("0"),bd("0"),bd("10"),
                List.of(Source.direct("SALES_ORDER",1L,"SO1",2L,bd("12"))),List.of(),make?List.of(new Material(88L,bd("2"),bd("1"),bd("0"))):List.of());
        var snapshot = new MrpCalculationSnapshot(2,List.of(node),List.of());
        run.setCalculationSnapshot(codec.write(snapshot));
        line.setProductId(83L);line.setSuggestionType(node.supplyType());line.setDemandQty(node.demandQty());line.setOnHandQty(node.onHandQty());
        line.setOpenSupplyQty(node.openSupplyQty());line.setNetQty(node.netQty());line.setWarehouseId(node.warehouseId());line.setMaterialWarehouseId(node.materialWarehouseId());
        line.setBomId(node.bomId());line.setBomVersion(node.bomVersion());line.setCalculationTrace(codec.write(node));
        var computed = new ComputedLine(83L,node.supplyType(),node.demandQty(),node.onHandQty(),node.openSupplyQty(),node.netQty(),node.bomId(),"计算",node);
        when(calculation.calculate(AUDIT)).thenReturn(new CalculationResult(make?List.of(computed):List.of(),make?List.of():List.of(computed),snapshot));
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}

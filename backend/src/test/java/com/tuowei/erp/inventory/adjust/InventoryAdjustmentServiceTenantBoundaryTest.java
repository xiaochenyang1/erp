package com.tuowei.erp.inventory.adjust;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentNumberService;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineRequest;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAdjustmentServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final Long ADJUSTMENT_ID = 8001L;
    private static final Long WAREHOUSE_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 6, 8);

    @Mock
    private InventoryAdjustmentMapper adjustmentMapper;

    @Mock
    private InventoryAdjustmentLineMapper lineMapper;

    @Mock
    private InventoryAdjustmentNumberService numberService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private AttachmentService attachmentService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryAdjustmentLineEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(ProductEntity.class);
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any())).thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createRejectsNullRequestBeforeInsertingAdjustment() {
        assertThatThrownBy(() -> service().create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调整单明细不能为空");

        verify(adjustmentMapper, never()).insert(any(InventoryAdjustmentEntity.class));
        verify(lineMapper, never()).insert(any(InventoryAdjustmentLineEntity.class));
    }

    @Test
    void createRejectsNullLinesBeforeInsertingAdjustment() {
        assertThatThrownBy(() -> service().create(new InventoryAdjustmentCreateRequest(
                        WAREHOUSE_ID,
                        BIZ_DATE,
                        "missing lines",
                        null
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调整单明细不能为空");

        verify(adjustmentMapper, never()).insert(any(InventoryAdjustmentEntity.class));
        verify(lineMapper, never()).insert(any(InventoryAdjustmentLineEntity.class));
    }

    @Test
    void createRejectsEmptyLinesBeforeInsertingAdjustment() {
        assertThatThrownBy(() -> service().create(new InventoryAdjustmentCreateRequest(
                        WAREHOUSE_ID,
                        BIZ_DATE,
                        "empty lines",
                        List.of()
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调整单明细不能为空");

        verify(adjustmentMapper, never()).insert(any(InventoryAdjustmentEntity.class));
        verify(lineMapper, never()).insert(any(InventoryAdjustmentLineEntity.class));
    }

    @Test
    void getByIdRejectsDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(adjustmentMapper.selectById(ADJUSTMENT_ID)).thenReturn(adjustment(9999L));

        assertThatThrownBy(() -> service().getById(ADJUSTMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调整单不存在");
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(adjustmentMapper.selectById(ADJUSTMENT_ID)).thenReturn(adjustment(AUDIT.accountBookId()));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        service().getById(ADJUSTMENT_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<InventoryAdjustmentLineEntity>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("adjustment_id")
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void postRejectsDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(adjustmentMapper.selectById(ADJUSTMENT_ID)).thenReturn(adjustment(9999L));

        assertThatThrownBy(() -> service().post(ADJUSTMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调整单不存在");
    }

    @Test
    void postTriggersFinancePostingForReconciliation() {
        InventoryAdjustmentEntity adjustment = adjustment(AUDIT.accountBookId());
        InventoryAdjustmentLineEntity inboundLine = adjustmentLine(9101L, 1, "IN", "12.00");
        InventoryAdjustmentLineEntity outboundLine = adjustmentLine(9102L, 2, "OUT", "5.00");
        List<InventoryAdjustmentLineEntity> lines = List.of(inboundLine, outboundLine);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(adjustmentMapper.selectById(ADJUSTMENT_ID)).thenReturn(adjustment);
        when(lineMapper.selectList(any())).thenReturn(lines);
        when(inventoryPostingService.postOutbound(any(), any(), any())).thenReturn(new BigDecimal("5.00"));
        when(adjustmentMapper.updateById(any(InventoryAdjustmentEntity.class))).thenReturn(1);

        service().post(ADJUSTMENT_ID);

        verify(financePostingService).recordInventoryAdjustment(adjustment, lines, AUDIT);
    }

    @Test
    void postStopsAtAttachmentGateBeforeCheckingAccountingPeriod() {
        InventoryAdjustmentEntity adjustment = adjustment(AUDIT.accountBookId());
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(adjustmentMapper.selectById(ADJUSTMENT_ID)).thenReturn(adjustment);
        doThrow(new IllegalArgumentException("业务类型 INVENTORY_ADJUSTMENT 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("INVENTORY_ADJUSTMENT", ADJUSTMENT_ID);

        assertThatThrownBy(() -> service().post(ADJUSTMENT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVENTORY_ADJUSTMENT");

        assertThat(adjustment.getStatus()).isEqualTo("DRAFT");
        verify(accountPeriodGuard, never()).requireOpen(any(), any());
        verify(adjustmentMapper, never()).updateById(any(InventoryAdjustmentEntity.class));
        verify(inventoryPostingService, never()).postInbound(any(), any());
        verify(financePostingService, never()).recordInventoryAdjustment(any(), any(), any());
    }

    private InventoryAdjustmentService service() {
        return new InventoryAdjustmentService(
                adjustmentMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                inventorySerialNumberService,
                financePostingService,
                auditMetadataFactory,
                warehouseMapper,
                productValidator,
                accountPeriodGuard,
                attachmentService
        );
    }

    private InventoryAdjustmentCreateRequest createRequest() {
        return new InventoryAdjustmentCreateRequest(
                WAREHOUSE_ID,
                BIZ_DATE,
                "tenant boundary",
                List.of(new InventoryAdjustmentLineRequest(
                        PRODUCT_ID,
                        "IN",
                        new BigDecimal("1.0000"),
                        new BigDecimal("10.0000"),
                        "tenant boundary"
                ))
        );
    }

    private InventoryAdjustmentEntity adjustment(Long accountBookId) {
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.setId(ADJUSTMENT_ID);
        adjustment.setCompanyId(AUDIT.companyId());
        adjustment.setAccountBookId(accountBookId);
        adjustment.setAdjustmentNo("ADJ-20260608-001");
        adjustment.setWarehouseId(WAREHOUSE_ID);
        adjustment.setAdjustmentDate(BIZ_DATE);
        adjustment.setStatus("DRAFT");
        adjustment.setDeletedFlag(0);
        adjustment.setTotalQuantity(BigDecimal.ZERO);
        adjustment.setTotalAmount(BigDecimal.ZERO);
        return adjustment;
    }

    private InventoryAdjustmentLineEntity adjustmentLine(Long id, int lineNo, String direction, String amount) {
        InventoryAdjustmentLineEntity line = new InventoryAdjustmentLineEntity();
        line.setId(id);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setAdjustmentId(ADJUSTMENT_ID);
        line.setLineNo(lineNo);
        line.setProductId(PRODUCT_ID);
        line.setDirection(direction);
        line.setQty(new BigDecimal("1.0000"));
        line.setUnitCost(new BigDecimal(amount));
        line.setAmount(new BigDecimal(amount));
        line.setReason("reconciliation");
        line.setRemark("reconciliation");
        return line;
    }

    private WarehouseEntity activeWarehouse(Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCompanyId(AUDIT.companyId());
        warehouse.setAccountBookId(accountBookId);
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private ProductEntity activeProduct(Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(accountBookId);
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
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

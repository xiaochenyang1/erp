package com.tuowei.erp.qc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionCreateService;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.qc.inspection.service.QcInspectionNumberService;
import com.tuowei.erp.qc.inspection.service.QcInspectionSourceAccess;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcInspectionCreateServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9701L;
    private static final LocalDate INSPECTION_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 10, 0);

    @Mock
    private QcInspectionOrderMapper qcInspectionOrderMapper;

    @Mock
    private QcInspectionLineMapper qcInspectionLineMapper;

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private ProductionOrderMapper productionOrderMapper;

    @Mock
    private QcInspectionNumberService qcInspectionNumberService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(QcInspectionOrderEntity.class);
        initTableInfo(PurchaseReceiptLineEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
    }

    @Test
    void createIqcBuildsDraftFromTenantScopedReceiptLines() {
        stubAudit();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));
        when(qcInspectionNumberService.nextInspectionNo(INSPECTION_DATE)).thenReturn("QC202607130001");
        assignInspectionId(5001L);

        QcInspectionCreateService.CreationResult result = service().create(iqcCreate(7001L));

        assertThat(result.inspection().getInspectionNo()).isEqualTo("QC202607130001");
        assertThat(result.inspection().getInspectionType()).isEqualTo(QcInspectionGate.TYPE_IQC);
        assertThat(result.inspection().getStatus()).isEqualTo("DRAFT");
        assertThat(result.inspection().getReceiptId()).isEqualTo(7001L);
        assertThat(result.inspection().getOrderId()).isEqualTo(6001L);
        assertThat(result.inspection().getWarehouseId()).isEqualTo(3001L);
        assertThat(result.inspection().getTotalQty()).isEqualByComparingTo("5.0000");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.getInspectionId()).isEqualTo(5001L);
            assertThat(line.getReceiptLineId()).isEqualTo(8001L);
            assertThat(line.getDeliveryLineId()).isNull();
            assertThat(line.getInspectedQty()).isEqualByComparingTo("5.0000");
            assertThat(line.getQualifiedQty()).isEqualByComparingTo("0.0000");
            assertThat(line.getCreatedBy()).isEqualTo(USER_ID);
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectList(wrapperCaptor.capture());
        assertSourceLineScoped(wrapperCaptor.getValue(), "receipt_id");
    }

    @Test
    void createOqcBuildsDraftFromTenantScopedDeliveryLines() {
        stubAudit();
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(draftDelivery());
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine()));
        when(qcInspectionNumberService.nextInspectionNo(INSPECTION_DATE)).thenReturn("QC202607130099");
        assignInspectionId(5101L);

        QcInspectionCreateService.CreationResult result = service().create(oqcCreate(9101L));

        assertThat(result.inspection().getInspectionType()).isEqualTo(QcInspectionGate.TYPE_OQC);
        assertThat(result.inspection().getReceiptId()).isNull();
        assertThat(result.inspection().getDeliveryId()).isEqualTo(9101L);
        assertThat(result.inspection().getOrderId()).isEqualTo(9001L);
        assertThat(result.inspection().getTotalQty()).isEqualByComparingTo("3.0000");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.getInspectionId()).isEqualTo(5101L);
            assertThat(line.getReceiptLineId()).isNull();
            assertThat(line.getDeliveryLineId()).isEqualTo(9201L);
            assertThat(line.getInspectedQty()).isEqualByComparingTo("3.0000");
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(wrapperCaptor.capture());
        assertSourceLineScoped(wrapperCaptor.getValue(), "delivery_id");
    }

    @Test
    void createIpqcBuildsOneFinishedProductLine() {
        stubAudit();
        when(productionOrderMapper.selectById(9301L)).thenReturn(productionOrder(
                ProductionOrderService.STATUS_RELEASED,
                COMPANY_ID,
                ACCOUNT_BOOK_ID
        ));
        when(qcInspectionOrderMapper.selectCount(any())).thenReturn(0L);
        when(qcInspectionNumberService.nextInspectionNo(INSPECTION_DATE)).thenReturn("QC202607130188");
        assignInspectionId(5201L);

        QcInspectionCreateService.CreationResult result = service().create(ipqcCreate(9301L));

        assertThat(result.inspection().getInspectionType()).isEqualTo(QcInspectionGate.TYPE_IPQC);
        assertThat(result.inspection().getReceiptId()).isNull();
        assertThat(result.inspection().getDeliveryId()).isNull();
        assertThat(result.inspection().getProductionOrderId()).isEqualTo(9301L);
        assertThat(result.inspection().getOrderId()).isEqualTo(9301L);
        assertThat(result.inspection().getWarehouseId()).isEqualTo(3301L);
        assertThat(result.inspection().getTotalQty()).isEqualByComparingTo("7.5000");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.getInspectionId()).isEqualTo(5201L);
            assertThat(line.getProductId()).isEqualTo(4301L);
            assertThat(line.getInspectedQty()).isEqualByComparingTo("7.5000");
            assertThat(line.getRemark()).isEqualTo("过程检-成品");
        });
    }

    @Test
    void createIqcRejectsActiveInspectionAndScopesGuardToTenant() {
        stubAudit();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(qcInspectionOrderMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().create(iqcCreate(7001L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该采购入库单已存在有效的检验单");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<QcInspectionOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(qcInspectionOrderMapper).exists(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("inspection_type")
                .contains("receipt_id")
                .contains("deleted_flag")
                .contains("status");
        verify(purchaseReceiptLineMapper, never()).selectList(any());
    }

    @Test
    void createOqcRejectsActiveInspectionAndScopesGuardToTenant() {
        stubAudit();
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(draftDelivery());
        when(qcInspectionOrderMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().create(oqcCreate(9101L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该销售出库单已存在有效的检验单");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<QcInspectionOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(qcInspectionOrderMapper).exists(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("inspection_type")
                .contains("delivery_id")
                .contains("deleted_flag")
                .contains("status");
        verify(salesDeliveryLineMapper, never()).selectList(any());
    }

    @Test
    void createIqcRejectsReceiptOutsideCurrentTenant() {
        stubAudit();
        PurchaseReceiptEntity receipt = draftReceipt();
        receipt.setAccountBookId(ACCOUNT_BOOK_ID + 1);
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt);

        assertThatThrownBy(() -> service().create(iqcCreate(7001L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单不存在");

        verify(qcInspectionOrderMapper, never()).insert(any(QcInspectionOrderEntity.class));
    }

    @Test
    void createOqcRejectsDeliveryOutsideCurrentTenant() {
        stubAudit();
        SalesDeliveryEntity delivery = draftDelivery();
        delivery.setCompanyId(COMPANY_ID + 1);
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(delivery);

        assertThatThrownBy(() -> service().create(oqcCreate(9101L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售出库单不存在");

        verify(qcInspectionOrderMapper, never()).insert(any(QcInspectionOrderEntity.class));
    }

    @Test
    void createRejectsNonDraftReceiptAndDelivery() {
        stubAudit();
        PurchaseReceiptEntity receipt = draftReceipt();
        receipt.setStatus("POSTED");
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt);

        assertThatThrownBy(() -> service().create(iqcCreate(7001L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单不是草稿状态，不能进行来料检验");

        SalesDeliveryEntity delivery = draftDelivery();
        delivery.setStatus("POSTED");
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(delivery);

        assertThatThrownBy(() -> service().create(oqcCreate(9101L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售出库单不是草稿状态，不能进行出库检验");
    }

    @Test
    void createIpqcRejectsInvalidStatusAndTenant() {
        stubAudit();
        when(productionOrderMapper.selectById(9301L)).thenReturn(productionOrder(
                ProductionOrderService.STATUS_DRAFT,
                COMPANY_ID,
                ACCOUNT_BOOK_ID
        ));

        assertThatThrownBy(() -> service().create(ipqcCreate(9301L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅已下达/已领料的生产工单可做过程检");

        when(productionOrderMapper.selectById(9301L)).thenReturn(productionOrder(
                ProductionOrderService.STATUS_RELEASED,
                COMPANY_ID,
                ACCOUNT_BOOK_ID + 1
        ));

        assertThatThrownBy(() -> service().create(ipqcCreate(9301L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生产工单不存在");
    }

    @Test
    void createIpqcRejectsActiveInspectionAndScopesGuardToTenant() {
        stubAudit();
        when(productionOrderMapper.selectById(9301L)).thenReturn(productionOrder(
                ProductionOrderService.STATUS_MATERIAL_ISSUED,
                COMPANY_ID,
                ACCOUNT_BOOK_ID
        ));
        when(qcInspectionOrderMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().create(ipqcCreate(9301L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该生产工单已有进行中的过程检");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<QcInspectionOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(qcInspectionOrderMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("inspection_type")
                .contains("production_order_id")
                .contains("deleted_flag")
                .contains("status");
        verify(qcInspectionOrderMapper, never()).insert(any(QcInspectionOrderEntity.class));
    }

    @Test
    void createDeclaresWriteTransaction() throws NoSuchMethodException {
        Transactional transactional = QcInspectionCreateService.class
                .getMethod("create", QcInspectionCreateRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void stubAudit() {
        when(auditMetadataFactory.current())
                .thenReturn(new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW));
    }

    private void assignInspectionId(Long id) {
        when(qcInspectionOrderMapper.insert(any(QcInspectionOrderEntity.class))).thenAnswer(invocation -> {
            QcInspectionOrderEntity inspection = invocation.getArgument(0);
            inspection.setId(id);
            return 1;
        });
    }

    private QcInspectionCreateRequest iqcCreate(Long receiptId) {
        return new QcInspectionCreateRequest(
                QcInspectionGate.TYPE_IQC,
                receiptId,
                null,
                null,
                INSPECTION_DATE,
                "来料"
        );
    }

    private QcInspectionCreateRequest oqcCreate(Long deliveryId) {
        return new QcInspectionCreateRequest(
                QcInspectionGate.TYPE_OQC,
                null,
                deliveryId,
                null,
                INSPECTION_DATE,
                "出库检"
        );
    }

    private QcInspectionCreateRequest ipqcCreate(Long productionOrderId) {
        return new QcInspectionCreateRequest(
                QcInspectionGate.TYPE_IPQC,
                null,
                null,
                productionOrderId,
                INSPECTION_DATE,
                "过程检"
        );
    }

    private PurchaseReceiptEntity draftReceipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(7001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine() {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(8001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptId(7001L);
        entity.setLineNo(1);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setRemark("receipt line");
        return entity;
    }

    private SalesDeliveryEntity draftDelivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(9101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryNo("SD-9101");
        entity.setOrderId(9001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(9201L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryId(9101L);
        entity.setLineNo(1);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setRemark("delivery line");
        return entity;
    }

    private ProductionOrderEntity productionOrder(String status, Long companyId, Long accountBookId) {
        ProductionOrderEntity entity = new ProductionOrderEntity();
        entity.setId(9301L);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setOrderNo("MO-9301");
        entity.setProductId(4301L);
        entity.setFinishedWarehouseId(3301L);
        entity.setPlannedQty(new BigDecimal("7.5000"));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionCreateService service() {
        return new QcInspectionCreateService(
                qcInspectionOrderMapper,
                qcInspectionLineMapper,
                qcInspectionNumberService,
                auditMetadataFactory,
                new QcInspectionSourceAccess(
                        purchaseReceiptMapper,
                        purchaseReceiptLineMapper,
                        salesDeliveryMapper,
                        salesDeliveryLineMapper,
                        productionOrderMapper
                )
        );
    }

    private void assertSourceLineScoped(LambdaQueryWrapper<?> wrapper, String sourceIdColumn) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains(sourceIdColumn)
                .contains("line_no");
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

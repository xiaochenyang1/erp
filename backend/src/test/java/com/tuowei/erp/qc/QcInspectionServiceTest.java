package com.tuowei.erp.qc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.qc.inspection.service.QcInspectionNumberService;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeLineRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeRequest;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QcInspectionServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9701L;
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
        initTableInfo(QcInspectionLineEntity.class);
        initTableInfo(PurchaseReceiptLineEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
    }

    @Test
    void createBuildsInspectionLinesFromReceipt() {
        stubAudit();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(qcInspectionOrderMapper.exists(any())).thenReturn(false);
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));
        when(qcInspectionNumberService.nextInspectionNo(any())).thenReturn("QC202607130001");

        var response = service().create(iqcCreate(7001L, "来料"));

        assertThat(response.inspectionNo()).isEqualTo("QC202607130001");
        assertThat(response.inspectionType()).isEqualTo(QcInspectionGate.TYPE_IQC);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).inspectedQty()).isEqualByComparingTo("5.0000");
    }

    @Test
    void createRejectsWhenActiveInspectionExists() {
        stubAudit();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(qcInspectionOrderMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service().create(iqcCreate(7001L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该采购入库单已存在有效的检验单");
    }

    @Test
    void createRejectsNonDraftReceipt() {
        stubAudit();
        PurchaseReceiptEntity posted = draftReceipt();
        posted.setStatus("POSTED");
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(posted);

        assertThatThrownBy(() -> service().create(iqcCreate(7001L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单不是草稿状态，不能进行来料检验");
    }

    @Test
    void createOqcBuildsLinesFromDelivery() {
        stubAudit();
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(draftDelivery());
        when(qcInspectionOrderMapper.exists(any())).thenReturn(false);
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine()));
        when(qcInspectionNumberService.nextInspectionNo(any())).thenReturn("QC202607130099");

        var response = service().create(oqcCreate(9101L, "出库检"));

        assertThat(response.inspectionNo()).isEqualTo("QC202607130099");
        assertThat(response.inspectionType()).isEqualTo(QcInspectionGate.TYPE_OQC);
        assertThat(response.deliveryId()).isEqualTo(9101L);
        assertThat(response.receiptId()).isNull();
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).deliveryLineId()).isEqualTo(9201L);
        assertThat(response.lines().get(0).receiptLineId()).isNull();
        assertThat(response.lines().get(0).inspectedQty()).isEqualByComparingTo("3.0000");
    }

    @Test
    void judgeRejectsWhenQualifiedPlusUnqualifiedNotEqualInspected() {
        stubAudit();
        QcInspectionOrderEntity inspection = submittedInspectionIqc();
        when(qcInspectionOrderMapper.selectById(5001L)).thenReturn(inspection);
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(inspectionLineIqc()));
        lenient().when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));

        QcInspectionJudgeRequest request = new QcInspectionJudgeRequest(List.of(
                new QcInspectionJudgeLineRequest(6001L, new BigDecimal("3.0000"), new BigDecimal("1.0000"), null)
        ));

        assertThatThrownBy(() -> service().judge(5001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("合格数量与不合格数量之和必须等于检验数量");
    }

    @Test
    void judgeRejectsWhenInspectionNotSubmitted() {
        stubAudit();
        QcInspectionOrderEntity draft = submittedInspectionIqc();
        draft.setStatus("DRAFT");
        when(qcInspectionOrderMapper.selectById(5001L)).thenReturn(draft);

        QcInspectionJudgeRequest request = new QcInspectionJudgeRequest(List.of(
                new QcInspectionJudgeLineRequest(6001L, new BigDecimal("5.0000"), BigDecimal.ZERO, null)
        ));

        assertThatThrownBy(() -> service().judge(5001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前检验单状态不允许判定");
    }

    @Test
    void judgeWritesQualifiedQtyBackToReceiptLine() {
        stubAudit();
        when(qcInspectionOrderMapper.selectById(5001L)).thenReturn(submittedInspectionIqc());
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(draftReceipt());
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(inspectionLineIqc()));
        PurchaseReceiptLineEntity receiptLine = receiptLine();
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine));
        when(qcInspectionLineMapper.updateById(any(QcInspectionLineEntity.class))).thenReturn(1);
        when(qcInspectionOrderMapper.updateById(any(QcInspectionOrderEntity.class))).thenReturn(1);
        when(purchaseReceiptLineMapper.updateById(any(PurchaseReceiptLineEntity.class))).thenReturn(1);
        when(purchaseReceiptMapper.updateById(any(PurchaseReceiptEntity.class))).thenReturn(1);

        QcInspectionJudgeRequest request = new QcInspectionJudgeRequest(List.of(
                new QcInspectionJudgeLineRequest(6001L, new BigDecimal("4.0000"), new BigDecimal("1.0000"), "划痕")
        ));

        service().judge(5001L, request);

        assertThat(receiptLine.getQty()).isEqualByComparingTo("4.0000");
    }

    @Test
    void judgeOqcDoesNotRewriteDeliveryLine() {
        stubAudit();
        when(qcInspectionOrderMapper.selectById(5101L)).thenReturn(submittedInspectionOqc());
        when(salesDeliveryMapper.selectById(9101L)).thenReturn(draftDelivery());
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(inspectionLineOqc()));
        when(qcInspectionLineMapper.updateById(any(QcInspectionLineEntity.class))).thenReturn(1);
        when(qcInspectionOrderMapper.updateById(any(QcInspectionOrderEntity.class))).thenReturn(1);

        QcInspectionJudgeRequest request = new QcInspectionJudgeRequest(List.of(
                new QcInspectionJudgeLineRequest(6101L, new BigDecimal("2.0000"), new BigDecimal("1.0000"), "外观")
        ));

        service().judge(5101L, request);

        verify(salesDeliveryLineMapper, never()).updateById(any(SalesDeliveryLineEntity.class));
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
    }

    private void stubAudit() {
        lenient().when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW));
    }

    private QcInspectionCreateRequest iqcCreate(Long receiptId, String remark) {
        return new QcInspectionCreateRequest(QcInspectionGate.TYPE_IQC, receiptId, null, null, LocalDate.of(2026, 7, 13), remark);
    }

    private QcInspectionCreateRequest oqcCreate(Long deliveryId, String remark) {
        return new QcInspectionCreateRequest(QcInspectionGate.TYPE_OQC, null, deliveryId, null, LocalDate.of(2026, 7, 13), remark);
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
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(BigDecimal.ZERO);
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
        return entity;
    }

    private QcInspectionOrderEntity submittedInspectionIqc() {
        QcInspectionOrderEntity entity = new QcInspectionOrderEntity();
        entity.setId(5001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionNo("QC202607130001");
        entity.setInspectionType(QcInspectionGate.TYPE_IQC);
        entity.setReceiptId(7001L);
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("SUBMITTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionOrderEntity submittedInspectionOqc() {
        QcInspectionOrderEntity entity = new QcInspectionOrderEntity();
        entity.setId(5101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionNo("QC202607130099");
        entity.setInspectionType(QcInspectionGate.TYPE_OQC);
        entity.setDeliveryId(9101L);
        entity.setOrderId(9001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("SUBMITTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionLineEntity inspectionLineIqc() {
        QcInspectionLineEntity entity = new QcInspectionLineEntity();
        entity.setId(6001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionId(5001L);
        entity.setLineNo(1);
        entity.setReceiptLineId(8001L);
        entity.setProductId(4001L);
        entity.setInspectedQty(new BigDecimal("5.0000"));
        entity.setQualifiedQty(BigDecimal.ZERO);
        entity.setUnqualifiedQty(BigDecimal.ZERO);
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionLineEntity inspectionLineOqc() {
        QcInspectionLineEntity entity = new QcInspectionLineEntity();
        entity.setId(6101L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionId(5101L);
        entity.setLineNo(1);
        entity.setDeliveryLineId(9201L);
        entity.setProductId(4001L);
        entity.setInspectedQty(new BigDecimal("3.0000"));
        entity.setQualifiedQty(BigDecimal.ZERO);
        entity.setUnqualifiedQty(BigDecimal.ZERO);
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionService service() {
        return new QcInspectionService(
                qcInspectionOrderMapper,
                qcInspectionLineMapper,
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                productionOrderMapper,
                qcInspectionNumberService,
                auditMetadataFactory
        );
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

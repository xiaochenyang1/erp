package com.tuowei.erp.qc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.qc.inspection.service.QcInspectionQueryService;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

class QcInspectionQueryServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9701L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 13, 10, 0);
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 8, 31);

    private final QcInspectionOrderMapper qcInspectionOrderMapper = mock(QcInspectionOrderMapper.class);
    private final QcInspectionLineMapper qcInspectionLineMapper = mock(QcInspectionLineMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(QcInspectionOrderEntity.class);
        initTableInfo(QcInspectionLineEntity.class);
    }

    @Test
    void listNormalizesAllFiltersClampsPaginationAndMapsSummary() {
        stubAudit();
        QcInspectionOrderEntity inspection = inspection();
        inspection.setInspectionType("oqc");
        when(qcInspectionOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<QcInspectionOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(inspection));
            page.setTotal(1L);
            return page;
        });
        QcInspectionPageQuery query = fullQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(inspection.getId());
            assertThat(summary.inspectionNo()).isEqualTo("QC-5001");
            assertThat(summary.inspectionType()).isEqualTo(QcInspectionGate.TYPE_OQC);
            assertThat(summary.receiptId()).isEqualTo(7001L);
            assertThat(summary.deliveryId()).isEqualTo(7101L);
            assertThat(summary.productionOrderId()).isEqualTo(7201L);
            assertThat(summary.orderId()).isEqualTo(6001L);
            assertThat(summary.warehouseId()).isEqualTo(3001L);
            assertThat(summary.supplierId()).isEqualTo(4001L);
            assertThat(summary.totalQty()).isEqualByComparingTo("5.0000");
            assertThat(summary.qualifiedQty()).isEqualByComparingTo("4.0000");
            assertThat(summary.unqualifiedQty()).isEqualByComparingTo("1.0000");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<QcInspectionOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<QcInspectionOrderEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(qcInspectionOrderMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertNormalizedQuery(queryCaptor.getValue());
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubAudit();
        when(qcInspectionOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<QcInspectionOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
    }

    @Test
    void getByIdScopesLineQueryAndMapsCompleteDetail() {
        stubAudit();
        QcInspectionOrderEntity inspection = inspection();
        QcInspectionLineEntity line = inspectionLine();
        when(qcInspectionOrderMapper.selectById(inspection.getId())).thenReturn(inspection);
        when(qcInspectionLineMapper.selectList(any())).thenReturn(List.of(line));

        var result = service().getById(inspection.getId());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<QcInspectionLineEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(qcInspectionLineMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "inspection_id", "deleted_flag", "line_no");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(COMPANY_ID, ACCOUNT_BOOK_ID, inspection.getId());
        assertThat(result.id()).isEqualTo(inspection.getId());
        assertThat(result.inspectionType()).isEqualTo(QcInspectionGate.TYPE_IQC);
        assertThat(result.lines()).singleElement().satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(line.getId());
            assertThat(detail.lineNo()).isEqualTo(1);
            assertThat(detail.receiptLineId()).isEqualTo(8001L);
            assertThat(detail.deliveryLineId()).isEqualTo(8101L);
            assertThat(detail.productId()).isEqualTo(9001L);
            assertThat(detail.inspectedQty()).isEqualByComparingTo("5.0000");
            assertThat(detail.qualifiedQty()).isEqualByComparingTo("4.0000");
            assertThat(detail.unqualifiedQty()).isEqualByComparingTo("1.0000");
            assertThat(detail.defectReason()).isEqualTo("scratch");
            assertThat(detail.remark()).isEqualTo("line remark");
        });
    }

    @Test
    void getByIdRejectsInspectionOutsideTenantBeforeLoadingLines() {
        stubAudit();
        QcInspectionOrderEntity inspection = inspection();
        inspection.setAccountBookId(ACCOUNT_BOOK_ID + 1);
        when(qcInspectionOrderMapper.selectById(inspection.getId())).thenReturn(inspection);

        assertThatThrownBy(() -> service().getById(inspection.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检验单不存在");

        verify(qcInspectionLineMapper, never()).selectList(any());
    }

    @Test
    void exportRestoresAuthenticationAndWritesTenantFilteredCsv() throws Exception {
        stubAudit();
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingAuthentication = mock(Authentication.class);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(capturedAuthentication);
        SecurityContextHolder.setContext(requestContext);
        try {
            var export = service().exportInspections(fullQuery());
            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(qcInspectionOrderMapper.selectList(any())).thenAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(capturedAuthentication);
                return List.of(inspection());
            });
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            export.writeTo(outputStream);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFFinspectionNo,inspectionType,receiptId,deliveryId,orderId,warehouseId,inspectionDate,status,totalQty,qualifiedQty,unqualifiedQty,remark\r\n")
                    .contains("QC-5001,IQC,7001,7101,6001,3001,2026-08-13,JUDGED,5.0000,4.0000,1.0000,query detail\r\n");
            @SuppressWarnings({"rawtypes", "unchecked"})
            ArgumentCaptor<LambdaQueryWrapper<QcInspectionOrderEntity>> queryCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(qcInspectionOrderMapper).selectList(queryCaptor.capture());
            assertNormalizedQuery(queryCaptor.getValue());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private QcInspectionQueryService service() {
        return new QcInspectionQueryService(
                qcInspectionOrderMapper,
                qcInspectionLineMapper,
                auditMetadataFactory
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current())
                .thenReturn(new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW));
    }

    private QcInspectionPageQuery fullQuery() {
        QcInspectionPageQuery query = new QcInspectionPageQuery();
        query.setKeyword("  QC-SCOPE  ");
        query.setReceiptId(7001L);
        query.setDeliveryId(7101L);
        query.setInspectionType("  oqc  ");
        query.setStatus("  judged  ");
        query.setInspectionDateFrom(DATE_FROM);
        query.setInspectionDateTo(DATE_TO);
        return query;
    }

    private void assertNormalizedQuery(LambdaQueryWrapper<QcInspectionOrderEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "company_id",
                        "account_book_id",
                        "deleted_flag",
                        "inspection_no",
                        "receipt_id",
                        "delivery_id",
                        "inspection_type",
                        "status",
                        "inspection_date"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).contains(
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                "%QC-SCOPE%",
                7001L,
                7101L,
                QcInspectionGate.TYPE_OQC,
                "JUDGED",
                DATE_FROM,
                DATE_TO
        );
    }

    private QcInspectionOrderEntity inspection() {
        QcInspectionOrderEntity entity = new QcInspectionOrderEntity();
        entity.setId(5001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionNo("QC-5001");
        entity.setInspectionType(QcInspectionGate.TYPE_IQC);
        entity.setReceiptId(7001L);
        entity.setDeliveryId(7101L);
        entity.setProductionOrderId(7201L);
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setSupplierId(4001L);
        entity.setInspectionDate(LocalDate.of(2026, 8, 13));
        entity.setStatus("JUDGED");
        entity.setTotalQty(new BigDecimal("5.0000"));
        entity.setQualifiedQty(new BigDecimal("4.0000"));
        entity.setUnqualifiedQty(new BigDecimal("1.0000"));
        entity.setRemark("query detail");
        entity.setDeletedFlag(0);
        return entity;
    }

    private QcInspectionLineEntity inspectionLine() {
        QcInspectionLineEntity entity = new QcInspectionLineEntity();
        entity.setId(6001L);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setInspectionId(5001L);
        entity.setLineNo(1);
        entity.setReceiptLineId(8001L);
        entity.setDeliveryLineId(8101L);
        entity.setProductId(9001L);
        entity.setInspectedQty(new BigDecimal("5.0000"));
        entity.setQualifiedQty(new BigDecimal("4.0000"));
        entity.setUnqualifiedQty(new BigDecimal("1.0000"));
        entity.setDefectReason("scratch");
        entity.setRemark("line remark");
        entity.setDeletedFlag(0);
        return entity;
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

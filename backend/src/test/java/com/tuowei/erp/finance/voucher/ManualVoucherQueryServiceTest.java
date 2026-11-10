package com.tuowei.erp.finance.voucher;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherLineMapper;
import com.tuowei.erp.finance.voucher.mapper.ManualVoucherMapper;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.model.ManualVoucherLineEntity;
import com.tuowei.erp.finance.voucher.service.ManualVoucherQueryService;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
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

class ManualVoucherQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 7, 10, 0)
    );
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 7, 31);

    private final ManualVoucherMapper manualVoucherMapper = mock(ManualVoucherMapper.class);
    private final ManualVoucherLineMapper manualVoucherLineMapper = mock(ManualVoucherLineMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ManualVoucherEntity.class);
        initTableInfo(ManualVoucherLineEntity.class);
    }

    @Test
    void listAppliesTenantFiltersPreservesPaginationAndMapsFullLines() {
        stubAudit();
        ManualVoucherEntity voucher = voucher();
        when(manualVoucherMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ManualVoucherEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(voucher));
            return page;
        });
        when(manualVoucherLineMapper.selectList(any())).thenReturn(lines());
        ManualVoucherPageQuery query = fullQuery();
        query.setPageNo(3);
        query.setPageSize(50);

        var result = service().list(query);

        assertThat(result.pageNo()).isEqualTo(3L);
        assertThat(result.pageSize()).isEqualTo(50L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(voucher.getId());
            assertThat(response.voucherNo()).isEqualTo("MV202607010001");
            assertThat(response.status()).isEqualTo("CANCELLED");
            assertThat(response.postedVoucherId()).isEqualTo(2001L);
            assertThat(response.reversalVoucherId()).isEqualTo(3001L);
            assertThat(response.cancelReason()).isEqualTo("录入错误");
            assertThat(response.lines()).hasSize(2);
            assertThat(response.lines().get(0).subjectCode()).isEqualTo("1001");
            assertThat(response.lines().get(1).creditAmount()).isEqualByComparingTo("100.00");
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<ManualVoucherEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ManualVoucherEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(manualVoucherMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(50L);
        assertNormalizedQuery(queryCaptor.getValue());
    }

    @Test
    void listUsesQueryDefaultsAndSkipsLineQueriesForEmptyPage() {
        stubAudit();
        when(manualVoucherMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ManualVoucherEntity> page = invocation.getArgument(0);
            page.setTotal(0L);
            page.setRecords(List.of());
            return page;
        });

        var result = service().list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        verify(manualVoucherLineMapper, never()).selectList(any());
    }

    @Test
    void detailScopesLineQueryAndMapsLifecycleFields() {
        stubAudit();
        ManualVoucherEntity voucher = voucher();
        when(manualVoucherMapper.selectById(voucher.getId())).thenReturn(voucher);
        when(manualVoucherLineMapper.selectList(any())).thenReturn(lines());

        var result = service().detail(voucher.getId());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<ManualVoucherLineEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(manualVoucherLineMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "voucher_id", "line_no");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), voucher.getId());
        assertThat(result.cancelledTime()).isEqualTo(LocalDateTime.of(2026, 7, 7, 10, 0));
        assertThat(result.createdTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    @Test
    void detailRejectsVoucherOutsideCurrentAccountBookBeforeLoadingLines() {
        stubAudit();
        ManualVoucherEntity voucher = voucher();
        voucher.setAccountBookId(99L);
        when(manualVoucherMapper.selectById(voucher.getId())).thenReturn(voucher);

        assertThatThrownBy(() -> service().detail(voucher.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手工凭证不存在");

        verify(manualVoucherLineMapper, never()).selectList(any());
    }

    private ManualVoucherQueryService service() {
        return new ManualVoucherQueryService(
                manualVoucherMapper,
                manualVoucherLineMapper,
                auditMetadataFactory
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private ManualVoucherPageQuery fullQuery() {
        ManualVoucherPageQuery query = new ManualVoucherPageQuery();
        query.setVoucherNo("  MV202607  ");
        query.setStatus("  CANCELLED  ");
        query.setDateFrom(DATE_FROM);
        query.setDateTo(DATE_TO);
        return query;
    }

    private void assertNormalizedQuery(LambdaQueryWrapper<ManualVoucherEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "company_id",
                        "account_book_id",
                        "deleted_flag",
                        "voucher_no",
                        "status",
                        "biz_date"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters).contains(
                AUDIT.companyId(),
                AUDIT.accountBookId(),
                "%MV202607%",
                "CANCELLED",
                DATE_FROM,
                DATE_TO
        );
    }

    private ManualVoucherEntity voucher() {
        ManualVoucherEntity voucher = new ManualVoucherEntity();
        voucher.setId(1001L);
        voucher.setCompanyId(AUDIT.companyId());
        voucher.setAccountBookId(AUDIT.accountBookId());
        voucher.setVoucherNo("MV202607010001");
        voucher.setBizDate(LocalDate.of(2026, 7, 1));
        voucher.setAmount(new BigDecimal("100.00"));
        voucher.setStatus("CANCELLED");
        voucher.setRemark("手工凭证");
        voucher.setPostedVoucherId(2001L);
        voucher.setReversalVoucherId(3001L);
        voucher.setRejectReason("曾驳回");
        voucher.setCancelReason("录入错误");
        voucher.setSubmittedTime(LocalDateTime.of(2026, 7, 1, 10, 0));
        voucher.setApprovedTime(LocalDateTime.of(2026, 7, 1, 11, 0));
        voucher.setPostedTime(LocalDateTime.of(2026, 7, 1, 12, 0));
        voucher.setCancelledTime(LocalDateTime.of(2026, 7, 7, 10, 0));
        voucher.setCreatedTime(LocalDateTime.of(2026, 7, 1, 9, 0));
        voucher.setDeletedFlag(0);
        return voucher;
    }

    private List<ManualVoucherLineEntity> lines() {
        return List.of(
                line(1L, 1, 101L, "1001", "现金", "100.00", "0.00"),
                line(2L, 2, 201L, "2001", "应付", "0.00", "100.00")
        );
    }

    private ManualVoucherLineEntity line(
            Long id,
            int lineNo,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String debit,
            String credit
    ) {
        ManualVoucherLineEntity line = new ManualVoucherLineEntity();
        line.setId(id);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setVoucherId(1001L);
        line.setLineNo(lineNo);
        line.setSubjectId(subjectId);
        line.setSubjectCode(subjectCode);
        line.setSubjectName(subjectName);
        line.setDebitAmount(new BigDecimal(debit));
        line.setCreditAmount(new BigDecimal(credit));
        line.setSummary("line-" + lineNo);
        line.setDeletedFlag(0);
        return line;
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

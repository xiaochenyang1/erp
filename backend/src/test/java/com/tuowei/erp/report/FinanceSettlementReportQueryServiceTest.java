package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.service.FinanceSettlementReportQueryService;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSettlementReportQueryServiceTest {

    @Mock
    private PayableMapper payableMapper;
    @Mock
    private ReceivableMapper receivableMapper;
    @Mock
    private FinanceSettlementReportMapper financeSettlementReportMapper;
    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    private FinanceSettlementReportQueryService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayableEntity.class);
        initTableInfo(ReceivableEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new FinanceSettlementReportQueryService(
                payableMapper,
                receivableMapper,
                financeSettlementReportMapper,
                financeSettlementScopeSupport,
                new ReportProperties(3, 2)
        );
        lenient().when(financeSettlementScopeSupport.applyPayableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(financeSettlementScopeSupport.applyReceivableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsPayablesWithNormalizedFiltersPaginationScopeAndRemainingAmount() {
        FinanceSettlementReportQuery query = new FinanceSettlementReportQuery();
        query.setDirection(" payable ");
        query.setPageNo(0);
        query.setPageSize(999);
        query.setPartnerId(301L);
        query.setStatus(" partially_settled ");
        query.setSourceType(" purchase_receipt ");
        query.setBizDateFrom(LocalDate.of(2026, 8, 1));
        query.setBizDateTo(LocalDate.of(2026, 8, 31));
        when(payableMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PayableEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(payable(1L, "AP-001")));
            return page;
        });

        var response = service.listFinanceSettlements(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.direction()).isEqualTo("PAYABLE");
            assertThat(record.bizNo()).isEqualTo("AP-001");
            assertThat(record.remainingAmount()).isEqualByComparingTo("60.00");
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("deleted_flag")
                .contains("supplier_id")
                .contains("status")
                .contains("source_type")
                .contains("biz_date");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("PARTIALLY_SETTLED", "PURCHASE_RECEIPT");
        verify(financeSettlementScopeSupport).applyPayableScope(any());
        verify(receivableMapper, never()).selectPage(any(), any());
    }

    @Test
    void listsReceivablesAndMapsMissingAmountsToScaledRemainingAmount() {
        FinanceSettlementReportQuery query = new FinanceSettlementReportQuery();
        query.setDirection("receivable");
        when(receivableMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ReceivableEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(receivable(2L, "AR-001")));
            return page;
        });

        var response = service.listFinanceSettlements(query);

        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.direction()).isEqualTo("RECEIVABLE");
            assertThat(record.partnerId()).isEqualTo(401L);
            assertThat(record.remainingAmount()).isEqualByComparingTo("80.00");
        });
        verify(financeSettlementScopeSupport).applyReceivableScope(any());
        verify(payableMapper, never()).selectPage(any(), any());
    }

    @Test
    void mergedDirectionUsesCountedDatabaseWindowAndAliasesBothWrappers() {
        FinanceSettlementReportQuery query = new FinanceSettlementReportQuery();
        query.setPageNo(3);
        query.setPageSize(2);
        when(payableMapper.selectCount(any())).thenReturn(3L);
        when(receivableMapper.selectCount(any())).thenReturn(3L);
        when(financeSettlementReportMapper.selectAllSettlementPage(any(), any(), eq(2L), eq(4L)))
                .thenReturn(List.of(mergedResponse("AR-003"), mergedResponse("AP-003")));

        var response = service.listFinanceSettlements(query);

        assertThat(response.total()).isEqualTo(6);
        assertThat(response.records()).extracting(FinanceSettlementReportResponse::bizNo)
                .containsExactly("AR-003", "AP-003");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> receivableCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(financeSettlementReportMapper).selectAllSettlementPage(
                payableCaptor.capture(), receivableCaptor.capture(), eq(2L), eq(4L)
        );
        assertThat(payableCaptor.getValue().getParamAlias()).isEqualTo("payableWrapper");
        assertThat(receivableCaptor.getValue().getParamAlias()).isEqualTo("receivableWrapper");
    }

    @Test
    void mergedDirectionReturnsEmptyPageWithoutUnionQueryWhenOffsetIsOutOfRange() {
        FinanceSettlementReportQuery query = new FinanceSettlementReportQuery();
        query.setPageNo(5);
        query.setPageSize(2);
        when(payableMapper.selectCount(any())).thenReturn(2L);
        when(receivableMapper.selectCount(any())).thenReturn(1L);

        var response = service.listFinanceSettlements(query);

        assertThat(response.total()).isEqualTo(3);
        assertThat(response.records()).isEmpty();
        verify(financeSettlementReportMapper, never()).selectAllSettlementPage(any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    void enforcesCombinedExportLimit() {
        when(payableMapper.selectCount(any())).thenReturn(2L);
        when(receivableMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.assertFinanceSettlementExportWithinLimit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出结果超过3行，请缩小筛选范围后重试");
    }

    @Test
    void streamsMergedExportUsingOffsetBatches() {
        when(financeSettlementReportMapper.selectAllSettlementPage(any(), any(), eq(2L), eq(0L)))
                .thenReturn(List.of(mergedResponse("AP-001"), mergedResponse("AR-001")));
        when(financeSettlementReportMapper.selectAllSettlementPage(any(), any(), eq(2L), eq(2L)))
                .thenReturn(List.of(mergedResponse("AP-002")));

        List<FinanceSettlementReportResponse> records = new ArrayList<>();
        service.streamFinanceSettlements(null, records::add);

        assertThat(records).extracting(FinanceSettlementReportResponse::bizNo)
                .containsExactly("AP-001", "AR-001", "AP-002");
        verify(financeSettlementReportMapper).selectAllSettlementPage(any(), any(), eq(2L), eq(0L));
        verify(financeSettlementReportMapper).selectAllSettlementPage(any(), any(), eq(2L), eq(2L));
    }

    private PayableEntity payable(long id, String bizNo) {
        PayableEntity entity = new PayableEntity();
        entity.setId(id);
        entity.setPayableNo(bizNo);
        entity.setSupplierId(301L);
        entity.setBizDate(LocalDate.of(2026, 8, 13));
        entity.setSourceType("PURCHASE_RECEIPT");
        entity.setSourceNo("PR-001");
        entity.setOriginalAmount(new BigDecimal("100.00"));
        entity.setSettledAmount(new BigDecimal("40.00"));
        entity.setStatus("PARTIALLY_SETTLED");
        return entity;
    }

    private ReceivableEntity receivable(long id, String bizNo) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(id);
        entity.setReceivableNo(bizNo);
        entity.setCustomerId(401L);
        entity.setBizDate(LocalDate.of(2026, 8, 13));
        entity.setSourceType("SALES_DELIVERY");
        entity.setSourceNo("SD-001");
        entity.setOriginalAmount(new BigDecimal("80.00"));
        entity.setSettledAmount(null);
        entity.setStatus("UNSETTLED");
        return entity;
    }

    private FinanceSettlementReportResponse mergedResponse(String bizNo) {
        return new FinanceSettlementReportResponse(
                1L,
                bizNo.startsWith("AP") ? "PAYABLE" : "RECEIVABLE",
                bizNo,
                501L,
                LocalDate.of(2026, 8, 13),
                "TEST",
                "SRC-001",
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("80.00"),
                "PARTIALLY_SETTLED"
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

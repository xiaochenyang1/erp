package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.report.mapper.ProductionCostLedgerMapper;
import com.tuowei.erp.report.mapper.ProductionCostLedgerRow;
import com.tuowei.erp.report.service.ProductionCostReportService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionCostReportServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9101L, 1L, 1L, 11L, 12L, "cost_report", "生产成本报表"
    );

    @Mock
    private ProductionOrderQueryService productionOrderQueryService;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private ProductionCostLedgerMapper productionCostLedgerMapper;

    private ProductionCostReportService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "production-cost-report-test");
        assistant.setCurrentNamespace(ProductionCostReportServiceTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProductEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProductionCostReportService(
                productionOrderQueryService,
                productMapper,
                new ReportProperties(100, 20),
                currentUserContext,
                productionCostLedgerMapper
        );
        when(productMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void missingLedgerSummaryRowMeansZeroInsteadOfAssumingBusinessWorkInProgress() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(productionOrderQueryService.list(any())).thenReturn(new PageResponse<>(
                1, 20, 2, List.of(order(1L, "20.00"), order(2L, "15.00"))
        ));
        when(productionCostLedgerMapper.summarize(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), List.of(1L, 2L)))
                .thenReturn(List.of(ledgerRow(1L, "20.00")));

        var rows = service.list(null).records();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productionCostLedgerAmount()).isEqualByComparingTo("20.00");
        assertThat(rows.get(0).reconciliationDifference()).isEqualByComparingTo("0.00");
        assertThat(rows.get(1).productionCostLedgerAmount()).isEqualByComparingTo("0.00");
        assertThat(rows.get(1).reconciliationDifference()).isEqualByComparingTo("15.00");
        assertThat(rows.get(1).costStatus()).isEqualTo("UNRECONCILED");
    }

    @Test
    void compatibilityConstructorRetainsBusinessWorkInProgressFallback() {
        ProductionCostReportService compatibilityService = new ProductionCostReportService(
                productionOrderQueryService, productMapper, new ReportProperties(100, 20)
        );
        when(productionOrderQueryService.list(any())).thenReturn(new PageResponse<>(
                1, 20, 1, List.of(order(3L, "12.00"))
        ));

        var row = compatibilityService.list(null).records().get(0);

        assertThat(row.productionCostLedgerAmount()).isEqualByComparingTo("12.00");
        assertThat(row.reconciliationDifference()).isEqualByComparingTo("0.00");
    }

    @Test
    void closedCostSeparatesOriginalAndSettledVarianceAndLeavesNoWip() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        ProductionOrderResponse closed = new ProductionOrderResponse(
                4L, "WO-4", 101L, 201L, 301L, 302L,
                new BigDecimal("10.0000"), new BigDecimal("10.0000"), null, null, "COMPLETED",
                new BigDecimal("50.00"), new BigDecimal("30.00"), BigDecimal.ZERO,
                new BigDecimal("75.00"), new BigDecimal("50.00"), new BigDecimal("25.00"), BigDecimal.ZERO,
                "CLOSED", new BigDecimal("5.00"), 801L, java.time.LocalDate.of(2026, 11, 8),
                null, List.of()
        );
        when(productionOrderQueryService.list(any())).thenReturn(new PageResponse<>(1, 20, 1, List.of(closed)));
        when(productionCostLedgerMapper.summarize(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), List.of(4L)))
                .thenReturn(List.of(ledgerRow(4L, "0.00")));

        var row = service.list(null).records().get(0);

        assertThat(row.costVarianceAmount()).isEqualByComparingTo("5.00");
        assertThat(row.settledCostVarianceAmount()).isEqualByComparingTo("5.00");
        assertThat(row.workInProgressCost()).isEqualByComparingTo("0.00");
        assertThat(row.reconciliationDifference()).isEqualByComparingTo("0.00");
        assertThat(row.costStatus()).isEqualTo("CLOSED");
    }

    private ProductionOrderResponse order(long id, String materialAmount) {
        return new ProductionOrderResponse(
                id,
                "WO-" + id,
                101L,
                201L,
                301L,
                302L,
                new BigDecimal("10.0000"),
                BigDecimal.ZERO,
                null,
                null,
                "IN_PROGRESS",
                new BigDecimal(materialAmount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "OPEN",
                BigDecimal.ZERO,
                null,
                null,
                null,
                List.of()
        );
    }

    private ProductionCostLedgerRow ledgerRow(long orderId, String ledgerAmount) {
        ProductionCostLedgerRow row = new ProductionCostLedgerRow();
        row.setOrderId(orderId);
        row.setLedgerAmount(new BigDecimal(ledgerAmount));
        return row;
    }
}

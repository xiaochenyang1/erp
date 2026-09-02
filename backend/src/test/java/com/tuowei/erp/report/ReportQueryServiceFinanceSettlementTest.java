package com.tuowei.erp.report;

import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.service.InventoryReportQueryService;
import com.tuowei.erp.report.service.OrderReportQueryService;
import com.tuowei.erp.report.service.FinanceSettlementReportQueryService;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceFinanceSettlementTest {

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private SalesOrderMapper salesOrderMapper;
    @Mock
    private InventoryBalanceMapper inventoryBalanceMapper;
    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;
    @Mock
    private PayableMapper payableMapper;
    @Mock
    private ReceivableMapper receivableMapper;
    @Mock
    private FinanceSettlementReportMapper financeSettlementReportMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ScopedUserResolver scopedUserResolver;
    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    private ReportQueryService reportQueryService;

    @BeforeEach
    void setUp() {
        reportQueryService = new ReportQueryService(
                new OrderReportQueryService(
                        purchaseOrderMapper,
                        salesOrderMapper,
                        currentUserContext,
                        dataScopeService,
                        scopedUserResolver,
                        new ReportProperties(5000, 500)
                ),
                new InventoryReportQueryService(
                        inventoryBalanceMapper,
                        inventoryTransactionMapper,
                        currentUserContext,
                        dataScopeService,
                        new ReportProperties(5000, 500)
                ),
                new FinanceSettlementReportQueryService(
                        payableMapper,
                        receivableMapper,
                        financeSettlementReportMapper,
                        financeSettlementScopeSupport,
                        new ReportProperties(5000, 500)
                )
        );
        when(financeSettlementScopeSupport.applyPayableScope(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(financeSettlementScopeSupport.applyReceivableScope(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void allDirectionSettlementPaginationUsesMergedDatabaseWindow() {
        FinanceSettlementReportQuery query = new FinanceSettlementReportQuery();
        query.setPageNo(50);
        query.setPageSize(2);
        when(payableMapper.selectCount(any())).thenReturn(120L);
        when(receivableMapper.selectCount(any())).thenReturn(120L);
        when(financeSettlementReportMapper.selectAllSettlementPage(any(), any(), eq(2L), eq(98L)))
                .thenReturn(List.of(
                        response(900051L, "PAYABLE", "AP-51", LocalDate.of(2026, 5, 20)),
                        response(900151L, "RECEIVABLE", "AR-51", LocalDate.of(2026, 5, 19))
                ));

        var response = reportQueryService.listFinanceSettlements(query);

        assertThat(response.pageNo()).isEqualTo(50);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.total()).isEqualTo(240);
        assertThat(response.records())
                .extracting(FinanceSettlementReportResponse::bizNo)
                .containsExactly("AP-51", "AR-51");
        verify(financeSettlementReportMapper).selectAllSettlementPage(any(), any(), eq(2L), eq(98L));
        verify(payableMapper, never()).selectPage(any(), any());
        verify(receivableMapper, never()).selectPage(any(), any());
    }

    private FinanceSettlementReportResponse response(long id, String direction, String bizNo, LocalDate bizDate) {
        return new FinanceSettlementReportResponse(
                id,
                direction,
                bizNo,
                7001L,
                bizDate,
                "TEST_" + direction,
                "SRC-" + id,
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "PARTIALLY_SETTLED"
        );
    }
}

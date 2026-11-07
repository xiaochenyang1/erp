package com.tuowei.erp.report;

import com.tuowei.erp.report.service.ReportExportService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.ProductionCostReportQuery;
import com.tuowei.erp.report.web.ProductionCostReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReportExportServiceStreamingTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void purchaseOrderExportReturnsStreamingResponseBody() throws Exception {
        ReportQueryService queryService = mock(ReportQueryService.class);
        PurchaseOrderReportQuery query = new PurchaseOrderReportQuery();
        Authentication authentication = authentication();
        SecurityContextHolder.getContext().setAuthentication(authentication);
        doAnswer(invocation -> {
            Consumer<OrderReportResponse> consumer = invocation.getArgument(1);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
            consumer.accept(new OrderReportResponse(
                    1L,
                    "PO-STREAM-001",
                    101L,
                    LocalDate.of(2026, 6, 1),
                    "APPROVED",
                    "APPROVED",
                    "NOT_RECEIVED",
                    new BigDecimal("1.0000"),
                    new BigDecimal("10.00"),
                    new BigDecimal("1.30"),
                    "CNY",
                    BigDecimal.ONE,
                    new BigDecimal("10.00"),
                    new BigDecimal("1.30")
            ));
            return null;
        }).when(queryService).streamPurchaseOrders(
                ArgumentMatchers.eq(query),
                ArgumentMatchers.any()
        );
        ReportExportService service = new ReportExportService(queryService);

        StreamingResponseBody body = service.exportPurchaseOrders(query);
        verify(queryService).assertPurchaseOrderExportWithinLimit(query);
        SecurityContextHolder.clearContext();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        body.writeTo(outputStream);

        assertThat(outputStream.toString(StandardCharsets.UTF_8))
                .contains("bizNo,partnerId,bizDate,status,approvalStatus,fulfillmentStatus,totalQuantity,totalAmount,totalTaxAmount,currencyCode,exchangeRate,baseTotalAmount,baseTotalTaxAmount")
                .contains("PO-STREAM-001,101,2026-06-01,APPROVED,APPROVED,NOT_RECEIVED,1.0000,10.00,1.30,CNY,1,10.00,1.30");
    }

    @Test
    void productionCostExportIncludesComponentAndLedgerReconciliationColumns() throws Exception {
        ReportQueryService queryService = mock(ReportQueryService.class);
        ProductionCostReportQuery query = new ProductionCostReportQuery();
        doAnswer(invocation -> {
            Consumer<ProductionCostReportResponse> consumer = invocation.getArgument(1);
            consumer.accept(new ProductionCostReportResponse(
                    1L, "WO-COST-001", 101L, "FG-001", "成品", "IN_PROGRESS",
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                    new BigDecimal("10.0000"), new BigDecimal("4.0000"), new BigDecimal("0.4000"),
                    new BigDecimal("100.00"), new BigDecimal("30.00"), new BigDecimal("20.00"), new BigDecimal("150.00"),
                    new BigDecimal("40.00"), new BigDecimal("12.00"), new BigDecimal("8.00"), new BigDecimal("60.00"),
                    new BigDecimal("60.00"), new BigDecimal("18.00"), new BigDecimal("12.00"), new BigDecimal("90.00"),
                    new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("15.0000"), "WIP"
            ));
            return null;
        }).when(queryService).streamProductionCosts(
                ArgumentMatchers.eq(query),
                ArgumentMatchers.any()
        );
        ReportExportService service = new ReportExportService(queryService);

        StreamingResponseBody body = service.exportProductionCosts(query);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        body.writeTo(outputStream);

        assertThat(outputStream.toString(StandardCharsets.UTF_8))
                .contains("materialCost,laborCost,overheadCost,totalInputCost")
                .contains("finishedMaterialCost,finishedLaborCost,finishedOverheadCost,finishedGoodsCost")
                .contains("materialWorkInProgressCost,laborWorkInProgressCost,overheadWorkInProgressCost,workInProgressCost,productionCostLedgerAmount,reconciliationDifference")
                .contains("WO-COST-001,FG-001,成品,IN_PROGRESS,2026-06-01,2026-06-02,10.0000,4.0000,0.4000,100.00,30.00,20.00,150.00,40.00,12.00,8.00,60.00,60.00,18.00,12.00,90.00,90.00,0,15.0000,WIP");
        verify(queryService).streamProductionCosts(
                ArgumentMatchers.eq(query),
                ArgumentMatchers.any()
        );
    }

    private Authentication authentication() {
        ErpPrincipal principal = new ErpPrincipal(
                9101L,
                1L,
                1L,
                11L,
                12L,
                "report_export_stream",
                "报表导出",
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", principal.getAuthorities());
    }
}

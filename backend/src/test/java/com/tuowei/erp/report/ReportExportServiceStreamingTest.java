package com.tuowei.erp.report;

import com.tuowei.erp.report.service.ReportExportService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.report.web.OrderReportResponse;
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
                .contains("bizNo,partnerId,bizDate,status,approvalStatus,fulfillmentStatus,totalQuantity,totalAmount,totalTaxAmount")
                .contains("PO-STREAM-001,101,2026-06-01,APPROVED,APPROVED,NOT_RECEIVED,1.0000,10.00,1.30");
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

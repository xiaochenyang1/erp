package com.tuowei.erp.report;

import com.tuowei.erp.report.controller.ReportController;
import com.tuowei.erp.report.service.ReportExportService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportControllerStreamingExportContractTest {

    @Test
    void reportExportEndpointsReturnStreamingResponseBodies() throws Exception {
        for (ExportEndpoint endpoint : exportEndpoints()) {
            Method method = ReportController.class.getMethod(endpoint.methodName(), endpoint.queryType());

            assertThat(method.getGenericReturnType().getTypeName())
                    .contains("StreamingResponseBody")
                    .doesNotContain("java.lang.String");
        }
    }

    @Test
    void reportExportResponsesUseSafeContentDisposition() {
        ReportQueryService queryService = mock(ReportQueryService.class);
        ReportExportService exportService = mock(ReportExportService.class);
        when(exportService.exportPurchaseOrders(any())).thenReturn(outputStream -> {
        });
        ReportController controller = new ReportController(queryService, exportService);

        ResponseEntity<StreamingResponseBody> response = controller.exportPurchaseOrders(new PurchaseOrderReportQuery());

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''purchase-orders.csv")
                .doesNotContain("filename=purchase-orders.csv");
    }

    private List<ExportEndpoint> exportEndpoints() {
        return List.of(
                new ExportEndpoint("exportPurchaseOrders", PurchaseOrderReportQuery.class),
                new ExportEndpoint("exportSalesOrders", SalesOrderReportQuery.class),
                new ExportEndpoint("exportInventoryBalances", InventoryBalanceReportQuery.class),
                new ExportEndpoint("exportInventoryTransactions", InventoryTransactionReportQuery.class),
                new ExportEndpoint("exportFinanceSettlements", FinanceSettlementReportQuery.class)
        );
    }

    private record ExportEndpoint(String methodName, Class<?> queryType) {
    }
}

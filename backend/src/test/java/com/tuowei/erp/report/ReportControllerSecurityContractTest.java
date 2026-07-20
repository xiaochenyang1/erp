package com.tuowei.erp.report;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.report.service.ReportExportService;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportQueryService reportQueryService;

    @MockitoBean
    private ReportExportService reportExportService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void purchaseOrderReportRequiresReportViewPermission() throws Exception {
        mockMvc.perform(get("/api/reports/purchase-orders")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(reportQueryService, reportExportService);
    }

    @Test
    @WithErpUser(authorities = "report:view")
    void purchaseOrdersBindQueryAndReturnPageResponse() throws Exception {
        when(reportQueryService.listPurchaseOrders(any(PurchaseOrderReportQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(orderReport())
        ));

        mockMvc.perform(get("/api/reports/purchase-orders")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "PO-2026")
                        .param("supplierId", "3001")
                        .param("orderDateFrom", "2026-01-01")
                        .param("orderDateTo", "2026-01-31")
                        .param("status", "OPEN")
                        .param("approvalStatus", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].bizNo").value("PO-2026-001"));

        ArgumentCaptor<PurchaseOrderReportQuery> queryCaptor = ArgumentCaptor.forClass(PurchaseOrderReportQuery.class);
        verify(reportQueryService).listPurchaseOrders(queryCaptor.capture());
        PurchaseOrderReportQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isEqualTo("PO-2026");
        assertThat(query.getSupplierId()).isEqualTo(3001L);
        assertThat(query.getOrderDateFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(query.getOrderDateTo()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(query.getStatus()).isEqualTo("OPEN");
        assertThat(query.getApprovalStatus()).isEqualTo("APPROVED");
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void financeSettlementExportRequiresReportViewPermission() throws Exception {
        mockMvc.perform(get("/api/reports/finance-settlements/export")
                        .param("direction", "RECEIVABLE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(reportQueryService, reportExportService);
    }

    @Test
    @WithErpUser(authorities = "report:view")
    void financeSettlementExportBindsQueryAndPreservesCsvHeaders() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("bizNo,remainingAmount\r\nFS-001,80.00\r\n");
        when(reportExportService.exportFinanceSettlements(any(FinanceSettlementReportQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/reports/finance-settlements/export")
                        .param("pageNo", "1")
                        .param("pageSize", "200")
                        .param("direction", "RECEIVABLE")
                        .param("partnerId", "5001")
                        .param("status", "UNSETTLED")
                        .param("sourceType", "SALES_ORDER")
                        .param("bizDateFrom", "2026-02-01")
                        .param("bizDateTo", "2026-02-28"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("bizNo,remainingAmount\r\nFS-001,80.00\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''finance-settlements.csv")
                .doesNotContain("filename=finance-settlements.csv");

        ArgumentCaptor<FinanceSettlementReportQuery> queryCaptor = ArgumentCaptor.forClass(FinanceSettlementReportQuery.class);
        verify(reportExportService).exportFinanceSettlements(queryCaptor.capture());
        FinanceSettlementReportQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(200);
        assertThat(query.getDirection()).isEqualTo("RECEIVABLE");
        assertThat(query.getPartnerId()).isEqualTo(5001L);
        assertThat(query.getStatus()).isEqualTo("UNSETTLED");
        assertThat(query.getSourceType()).isEqualTo("SALES_ORDER");
        assertThat(query.getBizDateFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(query.getBizDateTo()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @WithErpUser(authorities = "report:view")
    void financeSettlementsBindQueryAndReturnPageResponse() throws Exception {
        when(reportQueryService.listFinanceSettlements(any(FinanceSettlementReportQuery.class))).thenReturn(new PageResponse<>(
                1,
                20,
                1,
                List.of(financeSettlementReport())
        ));

        mockMvc.perform(get("/api/reports/finance-settlements")
                        .param("direction", "PAYABLE")
                        .param("partnerId", "5002")
                        .param("status", "SETTLED")
                        .param("sourceType", "PURCHASE_ORDER")
                        .param("bizDateFrom", "2026-03-01")
                        .param("bizDateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].bizNo").value("FS-001"))
                .andExpect(jsonPath("$.data.records[0].remainingAmount").value(80.00));

        ArgumentCaptor<FinanceSettlementReportQuery> queryCaptor = ArgumentCaptor.forClass(FinanceSettlementReportQuery.class);
        verify(reportQueryService).listFinanceSettlements(queryCaptor.capture());
        FinanceSettlementReportQuery query = queryCaptor.getValue();
        assertThat(query.getDirection()).isEqualTo("PAYABLE");
        assertThat(query.getPartnerId()).isEqualTo(5002L);
        assertThat(query.getStatus()).isEqualTo("SETTLED");
        assertThat(query.getSourceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(query.getBizDateFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(query.getBizDateTo()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    private static OrderReportResponse orderReport() {
        return new OrderReportResponse(
                1001L,
                "PO-2026-001",
                3001L,
                LocalDate.of(2026, 1, 15),
                "OPEN",
                "APPROVED",
                "PARTIAL",
                new BigDecimal("10.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("130.00")
        );
    }

    private static FinanceSettlementReportResponse financeSettlementReport() {
        return new FinanceSettlementReportResponse(
                2001L,
                "PAYABLE",
                "FS-001",
                5002L,
                LocalDate.of(2026, 3, 15),
                "PURCHASE_ORDER",
                "PO-2026-001",
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("80.00"),
                "SETTLED"
        );
    }
}

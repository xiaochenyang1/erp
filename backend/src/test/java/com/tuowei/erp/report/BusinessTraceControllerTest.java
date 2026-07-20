package com.tuowei.erp.report;

import com.tuowei.erp.report.service.BusinessTraceService;
import com.tuowei.erp.report.web.BusinessTraceDocumentResponse;
import com.tuowei.erp.report.web.BusinessTraceExceptionTicketResponse;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import com.tuowei.erp.report.web.BusinessTraceResponse;
import com.tuowei.erp.report.web.BusinessTraceSummaryResponse;
import com.tuowei.erp.report.web.BusinessTraceTimelineResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessTraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessTraceService businessTraceService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void businessTraceRequiresReportViewPermission() throws Exception {
        mockMvc.perform(get("/api/reports/business-traces")
                        .param("keyword", "SO-2026-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(businessTraceService);
    }

    @Test
    @WithErpUser(authorities = "report:view")
    void businessTraceBindsKeywordAndReturnsTrace() throws Exception {
        when(businessTraceService.trace(any(BusinessTraceQuery.class))).thenReturn(traceResponse());

        mockMvc.perform(get("/api/reports/business-traces")
                        .param("keyword", " SO-2026-001 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.keyword").value("SO-2026-001"))
                .andExpect(jsonPath("$.data.documents[0].documentType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.data.documents[0].bizNo").value("SO-2026-001"))
                .andExpect(jsonPath("$.data.timeline[0].eventType").value("ORDER"))
                .andExpect(jsonPath("$.data.exceptionTickets[0].ticketNo").value("ET-20260630-0001"))
                .andExpect(jsonPath("$.data.summary.documentCount").value(1))
                .andExpect(jsonPath("$.data.summary.openExceptionTicketCount").value(1));

        ArgumentCaptor<BusinessTraceQuery> queryCaptor = ArgumentCaptor.forClass(BusinessTraceQuery.class);
        verify(businessTraceService).trace(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getKeyword()).isEqualTo(" SO-2026-001 ");
    }

    private static BusinessTraceResponse traceResponse() {
        return new BusinessTraceResponse(
                "SO-2026-001",
                List.of(new BusinessTraceDocumentResponse(
                        "SALES_ORDER-1001",
                        "SALES_ORDER",
                        "销售订单",
                        1001L,
                        "SO-2026-001",
                        "客户 501",
                        "OPEN",
                        "APPROVED",
                        LocalDate.of(2026, 6, 30),
                        "CUSTOMER",
                        501L,
                        new BigDecimal("10.0000"),
                        new BigDecimal("1200.00"),
                        "/sales/orders?keyword=SO-2026-001"
                )),
                List.of(new BusinessTraceTimelineResponse(
                        "ORDER-SALES_ORDER-1001",
                        "ORDER",
                        "销售订单创建",
                        "SO-2026-001",
                        "销售订单 OPEN",
                        LocalDateTime.of(2026, 6, 30, 9, 0),
                        "OPEN",
                        "NORMAL",
                        "/sales/orders?keyword=SO-2026-001"
                )),
                List.of(new BusinessTraceExceptionTicketResponse(
                        9501L,
                        "ET-20260630-0001",
                        "DELIVERY_DELAY",
                        "HIGH",
                        "销售订单发货异常",
                        "SALES_ORDER",
                        1001L,
                        "SO-2026-001",
                        "OPEN",
                        9002L,
                        LocalDateTime.of(2026, 6, 30, 18, 0),
                        LocalDateTime.of(2026, 6, 30, 10, 30),
                        "/exception-tickets?keyword=ET-20260630-0001"
                )),
                new BusinessTraceSummaryResponse(
                        1,
                        1,
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.0000"),
                        0,
                        1
                ),
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }
}

package com.tuowei.erp.finance.receivable;

import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
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

import java.time.LocalDate;

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

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceivableControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceivableQueryService receivableQueryService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void receivableExportRequiresReceivableViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/receivables/export")
                        .param("customerId", "5001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(receivableQueryService);
    }

    @Test
    @WithErpUser(authorities = "finance:receivable:view")
    void receivableExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("""
                receivableNo,remainingAmount
                AR-2026-001,80.00
                """.replace("\n", "\r\n"));
        when(receivableQueryService.exportReceivables(any(ReceivablePageQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/finance/receivables/export")
                        .param("pageNo", "1")
                        .param("pageSize", "200")
                        .param("customerId", "5001")
                        .param("status", "UNSETTLED")
                        .param("sourceType", "SALES_ORDER")
                        .param("bizDateFrom", "2026-05-01")
                        .param("bizDateTo", "2026-05-31"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("receivableNo,remainingAmount\r\nAR-2026-001,80.00\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''receivables.csv")
                .doesNotContain("filename=receivables.csv");

        ArgumentCaptor<ReceivablePageQuery> queryCaptor = ArgumentCaptor.forClass(ReceivablePageQuery.class);
        verify(receivableQueryService).exportReceivables(queryCaptor.capture());
        ReceivablePageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(200);
        assertThat(query.getCustomerId()).isEqualTo(5001L);
        assertThat(query.getStatus()).isEqualTo("UNSETTLED");
        assertThat(query.getSourceType()).isEqualTo("SALES_ORDER");
        assertThat(query.getBizDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(query.getBizDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));
    }
}

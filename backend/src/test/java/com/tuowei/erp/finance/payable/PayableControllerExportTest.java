package com.tuowei.erp.finance.payable;

import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
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
class PayableControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayableQueryService payableQueryService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void payableExportRequiresPayableViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/payables/export")
                        .param("supplierId", "6001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(payableQueryService);
    }

    @Test
    @WithErpUser(authorities = "finance:payable:view")
    void payableExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("""
                payableNo,remainingAmount
                AP-2026-001,120.00
                """.replace("\n", "\r\n"));
        when(payableQueryService.exportPayables(any(PayablePageQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/finance/payables/export")
                        .param("pageNo", "1")
                        .param("pageSize", "200")
                        .param("supplierId", "6001")
                        .param("status", "UNSETTLED")
                        .param("sourceType", "PURCHASE_ORDER")
                        .param("bizDateFrom", "2026-06-01")
                        .param("bizDateTo", "2026-06-30"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("payableNo,remainingAmount\r\nAP-2026-001,120.00\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''payables.csv")
                .doesNotContain("filename=payables.csv");

        ArgumentCaptor<PayablePageQuery> queryCaptor = ArgumentCaptor.forClass(PayablePageQuery.class);
        verify(payableQueryService).exportPayables(queryCaptor.capture());
        PayablePageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(200);
        assertThat(query.getSupplierId()).isEqualTo(6001L);
        assertThat(query.getStatus()).isEqualTo("UNSETTLED");
        assertThat(query.getSourceType()).isEqualTo("PURCHASE_ORDER");
        assertThat(query.getBizDateFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(query.getBizDateTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }
}

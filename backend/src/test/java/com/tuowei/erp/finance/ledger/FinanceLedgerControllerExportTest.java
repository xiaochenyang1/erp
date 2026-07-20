package com.tuowei.erp.finance.ledger;

import com.tuowei.erp.finance.ledger.service.FinanceLedgerService;
import com.tuowei.erp.finance.ledger.web.LedgerQuery;
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
class FinanceLedgerControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinanceLedgerService financeLedgerService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void ledgerExportRequiresLedgerViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/ledger/export")
                        .param("subjectCode", "1001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(financeLedgerService);
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void ledgerExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("""
                bizDate,voucherId,subjectCode,debitAmount,creditAmount
                2026-06-18,9001,1001,100.00,0.00
                """.replace("\n", "\r\n"));
        when(financeLedgerService.exportLedger(any(LedgerQuery.class))).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/finance/ledger/export")
                        .param("subjectCode", "1001")
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo", "2026-06-30"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("bizDate,voucherId,subjectCode,debitAmount,creditAmount\r\n2026-06-18,9001,1001,100.00,0.00\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''finance-ledger.csv")
                .doesNotContain("filename=finance-ledger.csv");

        ArgumentCaptor<LedgerQuery> queryCaptor = ArgumentCaptor.forClass(LedgerQuery.class);
        verify(financeLedgerService).exportLedger(queryCaptor.capture());
        LedgerQuery query = queryCaptor.getValue();
        assertThat(query.getSubjectCode()).isEqualTo("1001");
        assertThat(query.getDateFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(query.getDateTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }
}

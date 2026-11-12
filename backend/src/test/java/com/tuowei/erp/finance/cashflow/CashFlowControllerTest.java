package com.tuowei.erp.finance.cashflow;

import com.tuowei.erp.finance.cashflow.service.CashFlowConfigurationService;
import com.tuowei.erp.finance.cashflow.service.CashFlowExportService;
import com.tuowei.erp.finance.cashflow.service.CashFlowService;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CashFlowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashFlowService cashFlowService;

    @MockitoBean
    private CashFlowConfigurationService configurationService;

    @MockitoBean
    private CashFlowExportService exportService;

    @MockitoBean
    private SystemLogService systemLogService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void requiresLedgerViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/financial-statements/cash-flow")
                        .param("dateFrom", "2026-11-01")
                        .param("dateTo", "2026-11-30"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cashFlowService);
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void returnsDirectCashFlowStatement() throws Exception {
        when(cashFlowService.cashFlow(any())).thenReturn(response());

        mockMvc.perform(get("/api/finance/financial-statements/cash-flow")
                        .param("dateFrom", "2026-11-01")
                        .param("dateTo", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formulaVersion").value("CASH_FLOW_DIRECT_V1"))
                .andExpect(jsonPath("$.data.netIncreaseInCash").value(100.00))
                .andExpect(jsonPath("$.data.reconciled").value(true));
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void configurationRequiresSubjectManagePermission() throws Exception {
        mockMvc.perform(get("/api/finance/financial-statements/cash-flow/configuration"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configurationService);
    }

    @Test
    @WithErpUser(authorities = "finance:subject:manage")
    void returnsVersionedConfiguration() throws Exception {
        when(configurationService.configuration()).thenReturn(new CashFlowConfigurationResponse(
                "CASH_FLOW_DIRECT_V1", List.of("1002"), List.of(), List.of()
        ));

        mockMvc.perform(get("/api/finance/financial-statements/cash-flow/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashSubjectCodes[0]").value("1002"));
    }

    @Test
    @WithErpUser(authorities = "finance:subject:manage")
    void updatesCashScopeAndCounterpartMappings() throws Exception {
        CashFlowConfigurationResponse configuration = new CashFlowConfigurationResponse(
                "CASH_FLOW_DIRECT_V1", List.of("1002"), List.of(), List.of()
        );
        when(configurationService.replaceCashSubjects(any())).thenReturn(configuration);
        when(configurationService.replaceItemSubjects(any(), any())).thenReturn(configuration);

        mockMvc.perform(put("/api/finance/financial-statements/cash-flow/configuration/cash-subjects")
                        .contentType("application/json")
                        .content("{\"subjectCodes\":[\"1002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formulaVersion").value("CASH_FLOW_DIRECT_V1"));

        mockMvc.perform(put("/api/finance/financial-statements/cash-flow/configuration/items/CFO_OTHER/subjects")
                        .contentType("application/json")
                        .content("{\"subjectCodes\":[\"1122\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashSubjectCodes[0]").value("1002"));

        verify(systemLogService).recordOperation(
                any(),
                eq("finance"),
                eq("update-cash-equivalent-subjects"),
                eq("CASH_FLOW_DIRECT_V1"),
                eq("SUCCESS"),
                eq("更新现金及现金等价物科目范围"),
                argThat(request -> request.getRequestURI().equals(
                        "/api/finance/financial-statements/cash-flow/configuration/cash-subjects"
                ))
        );
        verify(systemLogService).recordOperation(
                any(),
                eq("finance"),
                eq("update-cash-flow-item-mapping"),
                eq("CASH_FLOW_DIRECT_V1"),
                eq("SUCCESS"),
                eq("更新现金流量表项目科目映射"),
                argThat(request -> request.getRequestURI().equals(
                        "/api/finance/financial-statements/cash-flow/configuration/items/CFO_OTHER/subjects"
                ))
        );
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void streamsCashFlowCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv(
                "dateFrom,dateTo,itemCode,amount\r\n2026-11-01,2026-11-30,CFO_OTHER,100.00\r\n"
        );
        when(exportService.export(any())).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/finance/financial-statements/cash-flow/export")
                        .param("dateFrom", "2026-11-01")
                        .param("dateTo", "2026-11-30"))
                .andExpect(request().asyncStarted())
                .andReturn();
        response.release();

        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();
        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("cash-flow.csv");
    }

    private CashFlowResponse response() {
        return new CashFlowResponse(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "CASH_FLOW_DIRECT_V1",
                List.of(), List.of(), List.of(), amount("100"), amount("0"), amount("0"), amount("100"),
                amount("20"), amount("120"), amount("0"), true, List.of(), List.of()
        );
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

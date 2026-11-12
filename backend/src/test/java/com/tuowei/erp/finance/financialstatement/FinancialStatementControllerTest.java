package com.tuowei.erp.finance.financialstatement;

import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementConfigurationService;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementExportService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
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
class FinancialStatementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialStatementService financialStatementService;

    @MockitoBean
    private FinancialStatementConfigurationService configurationService;

    @MockitoBean
    private FinancialStatementExportService exportService;

    @MockitoBean
    private SystemLogService systemLogService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void statementsRequireLedgerViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/financial-statements/balance-sheet")
                        .param("asOfDate", "2026-10-31"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(financialStatementService);
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void returnsBalanceSheetAndIncomeStatement() throws Exception {
        when(financialStatementService.balanceSheet(any())).thenReturn(new BalanceSheetResponse(
                LocalDate.of(2026, 10, 31), "TYPE_MAPPING_V1", List.of(), List.of(), List.of(),
                amount("100"), amount("60"), amount("40"), amount("100"), amount("0"), true,
                List.of(), List.of()
        ));
        when(financialStatementService.incomeStatement(any())).thenReturn(new IncomeStatementResponse(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "TYPE_MAPPING_V1",
                List.of(), List.of(), amount("80"), amount("30"), amount("50"), List.of(), List.of()
        ));

        mockMvc.perform(get("/api/finance/financial-statements/balance-sheet")
                        .param("asOfDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalAssets").value(100.00));

        mockMvc.perform(get("/api/finance/financial-statements/income-statement")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netProfit").value(50.00));
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void configurationRequiresSubjectManagePermission() throws Exception {
        mockMvc.perform(get("/api/finance/financial-statements/configuration")
                        .param("statementType", "BALANCE_SHEET"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configurationService);
    }

    @Test
    @WithErpUser(authorities = "finance:subject:manage")
    void returnsVersionedConfiguration() throws Exception {
        when(configurationService.configuration("BALANCE_SHEET")).thenReturn(
                new FinancialStatementConfigurationResponse(
                        "TYPE_MAPPING_V1", "BALANCE_SHEET", List.of(), List.of()
                )
        );

        mockMvc.perform(get("/api/finance/financial-statements/configuration")
                        .param("statementType", "BALANCE_SHEET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formulaVersion").value("TYPE_MAPPING_V1"));
    }

    @Test
    @WithErpUser(authorities = "finance:subject:manage")
    void updatesStatementMappingWithOperationAudit() throws Exception {
        FinancialStatementConfigurationResponse configuration = new FinancialStatementConfigurationResponse(
                "TYPE_MAPPING_V1", "BALANCE_SHEET", List.of(), List.of()
        );
        when(configurationService.replaceSubjects(eq("BALANCE_SHEET"), eq("BS_CASH_BANK"), any()))
                .thenReturn(configuration);

        mockMvc.perform(put("/api/finance/financial-statements/configuration/BALANCE_SHEET/BS_CASH_BANK/subjects")
                        .contentType("application/json")
                        .content("{\"subjectCodes\":[\"1002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementType").value("BALANCE_SHEET"));

        verify(systemLogService).recordOperation(
                any(),
                eq("finance"),
                eq("update-financial-statement-mapping"),
                eq("BALANCE_SHEET"),
                eq("SUCCESS"),
                eq("更新财务报表科目映射"),
                argThat(request -> request.getRequestURI().equals(
                        "/api/finance/financial-statements/configuration/BALANCE_SHEET/BS_CASH_BANK/subjects"
                ))
        );
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void streamsBalanceSheetCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv(
                "dateFrom,dateTo,itemCode,amount\r\n2026-10-31,2026-10-31,BS_CASH_BANK,100.00\r\n"
        );
        when(exportService.exportBalanceSheet(any())).thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/finance/financial-statements/balance-sheet/export")
                        .param("asOfDate", "2026-10-31"))
                .andExpect(request().asyncStarted())
                .andReturn();
        response.release();

        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();
        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("balance-sheet.csv");
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

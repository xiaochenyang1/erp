package com.tuowei.erp.finance.financialstatement;

import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialStatementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialStatementService financialStatementService;

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
                amount("100"), amount("60"), amount("40"), amount("100"), amount("0"), true, List.of()
        ));
        when(financialStatementService.incomeStatement(any())).thenReturn(new IncomeStatementResponse(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "TYPE_MAPPING_V1",
                List.of(), List.of(), amount("80"), amount("30"), amount("50"), List.of()
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

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

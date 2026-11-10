package com.tuowei.erp.finance.ledger;

import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
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
class TrialBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrialBalanceService trialBalanceService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void requiresLedgerViewPermission() throws Exception {
        mockMvc.perform(get("/api/finance/ledger/trial-balance")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-31"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(trialBalanceService);
    }

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void returnsTrialBalance() throws Exception {
        when(trialBalanceService.trialBalance(any())).thenReturn(new TrialBalanceResponse(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                List.of(),
                amount("100"),
                amount("100"),
                amount("50"),
                amount("50"),
                amount("150"),
                amount("150"),
                true,
                true,
                true
        ));

        mockMvc.perform(get("/api/finance/ledger/trial-balance")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.data.openingBalanced").value(true))
                .andExpect(jsonPath("$.data.closingDebit").value(150.00));
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }
}

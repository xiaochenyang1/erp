package com.tuowei.erp.finance.currency;

import com.tuowei.erp.finance.currency.service.CurrencyAdminService;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrencyControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CurrencyAdminService service;

    @Test
    @WithErpUser(authorities = "masterdata:currency:view")
    void returnsConfiguredBaseCurrency() throws Exception {
        when(service.baseCurrency()).thenReturn("USD");
        mockMvc.perform(get("/api/finance/currencies/base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("USD"));
        verify(service).baseCurrency();
    }
}

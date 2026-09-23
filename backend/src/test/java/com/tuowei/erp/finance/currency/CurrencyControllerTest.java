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
import org.springframework.http.MediaType;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    @WithErpUser(authorities = "sales:order:create")
    void salesEditorCanReadCurrencyReferenceData() throws Exception {
        assertReferenceDataReadable();
    }

    @Test
    @WithErpUser(authorities = "purchase:order:update")
    void purchaseEditorCanReadCurrencyReferenceData() throws Exception {
        assertReferenceDataReadable();
    }

    @Test
    @WithErpUser(authorities = "sales:order:create")
    void orderEditorCannotManageCurrenciesOrBaseCurrency() throws Exception {
        mockMvc.perform(post("/api/finance/currencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"USD\",\"currencyName\":\"US Dollar\",\"decimalPlaces\":2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/finance/currencies/base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"USD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = "inventory:stock:view")
    void unrelatedPermissionDoesNotGrantCurrencyAccess() throws Exception {
        mockMvc.perform(get("/api/finance/currencies")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/currencies/base")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/currencies/rates")).andExpect(status().isForbidden());
    }

    private void assertReferenceDataReadable() throws Exception {
        when(service.baseCurrency()).thenReturn("USD");
        mockMvc.perform(get("/api/finance/currencies")).andExpect(status().isOk());
        mockMvc.perform(get("/api/finance/currencies/base"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("USD"));
        mockMvc.perform(get("/api/finance/currencies/rates").param("from", "CNY").param("to", "USD"))
                .andExpect(status().isOk());
    }
}

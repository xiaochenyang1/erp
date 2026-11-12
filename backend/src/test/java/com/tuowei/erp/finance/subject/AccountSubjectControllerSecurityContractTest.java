package com.tuowei.erp.finance.subject;

import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSubjectControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSubjectService accountSubjectService;

    @Test
    @WithErpUser(authorities = "finance:ledger:view")
    void ledgerViewerCanLoadSubjectTree() throws Exception {
        when(accountSubjectService.tree()).thenReturn(List.of());

        mockMvc.perform(get("/api/finance/account-subjects/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(accountSubjectService).tree();
    }

    @Test
    @WithErpUser(authorities = "finance:budget:view")
    void budgetViewerCanLoadSubjectTree() throws Exception {
        when(accountSubjectService.tree()).thenReturn(List.of());

        mockMvc.perform(get("/api/finance/account-subjects/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(accountSubjectService).tree();
    }

    @Test
    @WithErpUser(authorities = "finance:period:view")
    void unrelatedFinancePermissionCannotLoadSubjectTree() throws Exception {
        mockMvc.perform(get("/api/finance/account-subjects/tree"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(accountSubjectService);
    }
}

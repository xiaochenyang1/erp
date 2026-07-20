package com.tuowei.erp.system.observability;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessHealthRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithErpUser(authorities = "system:profile:view")
    void businessHealthRequiresObservabilityPermission() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = "system:observability:view")
    void businessHealthReturnsSummaryForAuthorizedUser() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").exists())
                .andExpect(jsonPath("$.data.generatedAt").exists())
                .andExpect(jsonPath("$.data.checks.length()").value(4))
                .andExpect(jsonPath("$.data.checks[0].code").value("READINESS_UNPASSED_P0_P1"));
    }
}

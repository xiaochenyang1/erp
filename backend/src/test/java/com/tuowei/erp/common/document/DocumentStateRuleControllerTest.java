package com.tuowei.erp.common.document;

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
class DocumentStateRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithErpUser(authorities = "purchase:order:view")
    void listRequiresSystemConfigViewPermission() throws Exception {
        mockMvc.perform(get("/api/system/document-state-rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));
    }

    @Test
    @WithErpUser(authorities = "system:config:view")
    void listReturnsStateRuleMatrix() throws Exception {
        mockMvc.perform(get("/api/system/document-state-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[?(@.documentType == 'PURCHASE_ORDER' && @.action == 'UNAPPROVE')].permission")
                        .value("purchase:order:unapprove"))
                .andExpect(jsonPath("$.data[?(@.documentType == 'SALES_ORDER' && @.action == 'UNAPPROVE')].permission")
                        .value("sales:order:unapprove"));
    }
}

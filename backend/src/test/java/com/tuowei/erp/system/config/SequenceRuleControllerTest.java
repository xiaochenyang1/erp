package com.tuowei.erp.system.config;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.service.SequenceRuleService;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SequenceRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SequenceRuleService sequenceRuleService;

    @Test
    @WithErpUser(authorities = "system:sequence-rule:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(sequenceRuleService.list(any(SequenceRulePageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(response(7001L, "SALES_ORDER", "ACTIVE"))
        ));

        mockMvc.perform(get("/api/system/sequence-rules")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "SALE")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].bizType").value("SALES_ORDER"));

        ArgumentCaptor<SequenceRulePageQuery> queryCaptor = ArgumentCaptor.forClass(SequenceRulePageQuery.class);
        verify(sequenceRuleService).list(queryCaptor.capture());
        SequenceRulePageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isEqualTo("SALE");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:view")
    void detailDelegatesToService() throws Exception {
        when(sequenceRuleService.getById(7001L)).thenReturn(response(7001L, "SALES_ORDER", "ACTIVE"));

        mockMvc.perform(get("/api/system/sequence-rules/{id}", 7001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7001))
                .andExpect(jsonPath("$.data.bizType").value("SALES_ORDER"));

        verify(sequenceRuleService).getById(7001L);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:view")
    void createRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/system/sequence-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(sequenceRuleService);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:create")
    void createRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/sequence-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prefix": "SO",
                                  "datePattern": "yyyyMMdd",
                                  "seqLength": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(sequenceRuleService);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:create")
    void createDelegatesValidatedPayloadToService() throws Exception {
        when(sequenceRuleService.create(any(SequenceRuleCreateRequest.class)))
                .thenReturn(response(7001L, "SALES_ORDER", "ACTIVE"));

        mockMvc.perform(post("/api/system/sequence-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7001))
                .andExpect(jsonPath("$.data.bizType").value("SALES_ORDER"));

        ArgumentCaptor<SequenceRuleCreateRequest> requestCaptor = ArgumentCaptor.forClass(SequenceRuleCreateRequest.class);
        verify(sequenceRuleService).create(requestCaptor.capture());
        SequenceRuleCreateRequest request = requestCaptor.getValue();
        assertThat(request.bizType()).isEqualTo("SALES_ORDER");
        assertThat(request.prefix()).isEqualTo("SO");
        assertThat(request.datePattern()).isEqualTo("yyyyMMdd");
        assertThat(request.seqLength()).isEqualTo(5);
        assertThat(request.currentValue()).isEqualTo(12L);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:update")
    void updateRejectsInvalidPayloadBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/system/sequence-rules/{id}", 7001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prefix": "",
                                  "datePattern": "yyyyMMdd",
                                  "seqLength": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(sequenceRuleService);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:update")
    void updateDelegatesValidatedPayloadToService() throws Exception {
        when(sequenceRuleService.update(eq(7001L), any(SequenceRuleUpdateRequest.class)))
                .thenReturn(response(7001L, "SALES_ORDER", "ACTIVE"));

        mockMvc.perform(put("/api/system/sequence-rules/{id}", 7001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prefix": "SO",
                                  "datePattern": "yyyyMMdd",
                                  "seqLength": 6,
                                  "currentValue": 120
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7001));

        ArgumentCaptor<SequenceRuleUpdateRequest> requestCaptor = ArgumentCaptor.forClass(SequenceRuleUpdateRequest.class);
        verify(sequenceRuleService).update(eq(7001L), requestCaptor.capture());
        SequenceRuleUpdateRequest request = requestCaptor.getValue();
        assertThat(request.prefix()).isEqualTo("SO");
        assertThat(request.datePattern()).isEqualTo("yyyyMMdd");
        assertThat(request.seqLength()).isEqualTo(6);
        assertThat(request.currentValue()).isEqualTo(120L);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:enable")
    void enableDelegatesToService() throws Exception {
        when(sequenceRuleService.enable(7001L)).thenReturn(response(7001L, "SALES_ORDER", "ACTIVE"));

        mockMvc.perform(post("/api/system/sequence-rules/{id}/enable", 7001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(sequenceRuleService).enable(7001L);
    }

    @Test
    @WithErpUser(authorities = "system:sequence-rule:disable")
    void disableDelegatesToService() throws Exception {
        when(sequenceRuleService.disable(7001L)).thenReturn(response(7001L, "SALES_ORDER", "DISABLED"));

        mockMvc.perform(post("/api/system/sequence-rules/{id}/disable", 7001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(sequenceRuleService).disable(7001L);
    }

    private static String validCreateBody() {
        return """
                {
                  "bizType": "SALES_ORDER",
                  "prefix": "SO",
                  "datePattern": "yyyyMMdd",
                  "seqLength": 5,
                  "currentValue": 12
                }
                """;
    }

    private static SequenceRuleResponse response(Long id, String bizType, String status) {
        return new SequenceRuleResponse(
                id,
                1L,
                1L,
                bizType,
                "SO",
                "yyyyMMdd",
                5,
                12L,
                status
        );
    }
}

package com.tuowei.erp.issue.sla;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExceptionSlaPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExceptionSlaPolicyService exceptionSlaPolicyService;

    @Test
    @WithErpUser(authorities = "exception-rule:view")
    void listRequiresExceptionSlaPolicyViewPermission() throws Exception {
        mockMvc.perform(get("/api/exception-sla-policies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionSlaPolicyService);
    }

    @Test
    @WithErpUser(authorities = "exception-sla-policy:view")
    void listBindsQueryAndReturnsPage() throws Exception {
        when(exceptionSlaPolicyService.list(any(ExceptionSlaPolicyPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(policy())
        ));

        mockMvc.perform(get("/api/exception-sla-policies")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("category", "PAYMENT_OVERDUE")
                        .param("priority", "HIGH")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].category").value("PAYMENT_OVERDUE"));

        ArgumentCaptor<ExceptionSlaPolicyPageQuery> queryCaptor =
                ArgumentCaptor.forClass(ExceptionSlaPolicyPageQuery.class);
        verify(exceptionSlaPolicyService).list(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCategory()).isEqualTo("PAYMENT_OVERDUE");
        assertThat(queryCaptor.getValue().getPriority()).isEqualTo("HIGH");
        assertThat(queryCaptor.getValue().getEnabled()).isTrue();
    }

    @Test
    @WithErpUser(authorities = "exception-sla-policy:view")
    void updateRequiresManagePermission() throws Exception {
        mockMvc.perform(put("/api/exception-sla-policies/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionSlaPolicyService);
    }

    @Test
    @WithErpUser(authorities = {"exception-sla-policy:view", "exception-sla-policy:manage"})
    void updateBindsBodyAndReturnsPolicy() throws Exception {
        when(exceptionSlaPolicyService.update(any(Long.class), any(ExceptionSlaPolicyUpdateRequest.class)))
                .thenReturn(policy());

        mockMvc.perform(put("/api/exception-sla-policies/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueHours").value(24))
                .andExpect(jsonPath("$.data.escalateToPriority").value("URGENT"));

        ArgumentCaptor<ExceptionSlaPolicyUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionSlaPolicyUpdateRequest.class);
        verify(exceptionSlaPolicyService).update(org.mockito.Mockito.eq(1001L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getDueHours()).isEqualTo(24);
        assertThat(requestCaptor.getValue().getEscalateToPriority()).isEqualTo("URGENT");
    }

    private static ExceptionSlaPolicyUpdateRequest updateRequest() {
        ExceptionSlaPolicyUpdateRequest request = new ExceptionSlaPolicyUpdateRequest();
        request.setDueHours(24);
        request.setEscalationEnabled(true);
        request.setEscalateToPriority("URGENT");
        request.setEnabled(true);
        request.setRemark("逾期收付高优先级 24 小时");
        return request;
    }

    private static ExceptionSlaPolicyResponse policy() {
        return new ExceptionSlaPolicyResponse(
                1001L,
                "PAYMENT_OVERDUE",
                "HIGH",
                24,
                true,
                "URGENT",
                true,
                "逾期收付高优先级 24 小时",
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }
}

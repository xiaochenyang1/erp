package com.tuowei.erp.issue.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitPageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleHitResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleScanResultResponse;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
class ExceptionRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExceptionRuleService exceptionRuleService;

    @Test
    @WithErpUser(authorities = "exception-ticket:view")
    void listRequiresExceptionRuleViewPermission() throws Exception {
        mockMvc.perform(get("/api/exception-rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionRuleService);
    }

    @Test
    @WithErpUser(authorities = "exception-rule:view")
    void listBindsQueryAndReturnsPage() throws Exception {
        when(exceptionRuleService.list(any(ExceptionRulePageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(rule())
        ));

        mockMvc.perform(get("/api/exception-rules")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "逾期")
                        .param("ruleType", "RECEIVABLE_OVERDUE")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].ruleCode").value("RECEIVABLE_OVERDUE_DEFAULT"));

        ArgumentCaptor<ExceptionRulePageQuery> queryCaptor = ArgumentCaptor.forClass(ExceptionRulePageQuery.class);
        verify(exceptionRuleService).list(queryCaptor.capture());
        ExceptionRulePageQuery query = queryCaptor.getValue();
        assertThat(query.getKeyword()).isEqualTo("逾期");
        assertThat(query.getRuleType()).isEqualTo("RECEIVABLE_OVERDUE");
        assertThat(query.getEnabled()).isTrue();
    }

    @Test
    @WithErpUser(authorities = "exception-rule:view")
    void updateRequiresManagePermission() throws Exception {
        mockMvc.perform(put("/api/exception-rules/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionRuleService);
    }

    @Test
    @WithErpUser(authorities = {"exception-rule:view", "exception-rule:manage"})
    void updateBindsBodyAndReturnsRule() throws Exception {
        when(exceptionRuleService.update(any(Long.class), any(ExceptionRuleUpdateRequest.class))).thenReturn(rule());

        mockMvc.perform(put("/api/exception-rules/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.thresholdValue").value(30));

        ArgumentCaptor<ExceptionRuleUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionRuleUpdateRequest.class);
        verify(exceptionRuleService).update(org.mockito.Mockito.eq(1001L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPriority()).isEqualTo("URGENT");
        assertThat(requestCaptor.getValue().getThresholdValue()).isEqualByComparingTo("30");
    }

    @Test
    @WithErpUser(authorities = "exception-rule:view")
    void scanRequiresExecutePermission() throws Exception {
        mockMvc.perform(post("/api/exception-rules/1001/scan"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionRuleService);
    }

    @Test
    @WithErpUser(authorities = "exception-rule:execute")
    void scanEndpointsDelegateToService() throws Exception {
        when(exceptionRuleService.scanRule(1001L)).thenReturn(scanResult());
        when(exceptionRuleService.scanAll()).thenReturn(List.of(scanResult()));

        mockMvc.perform(post("/api/exception-rules/1001/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.hitCount").value(2));

        mockMvc.perform(post("/api/exception-rules/scan-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ticketCreatedCount").value(1));

        verify(exceptionRuleService).scanRule(1001L);
        verify(exceptionRuleService).scanAll();
    }

    @Test
    @WithErpUser(authorities = "exception-rule:view")
    void hitsBindQueryAndReturnPage() throws Exception {
        when(exceptionRuleService.listHits(any(ExceptionRuleHitPageQuery.class))).thenReturn(new PageResponse<>(
                1,
                20,
                1,
                List.of(hit())
        ));

        mockMvc.perform(get("/api/exception-rules/hits")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("ruleType", "LOW_STOCK")
                        .param("sourceNo", "W:11/P:22")
                        .param("ticketId", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].ticketId").value(9001));

        ArgumentCaptor<ExceptionRuleHitPageQuery> queryCaptor =
                ArgumentCaptor.forClass(ExceptionRuleHitPageQuery.class);
        verify(exceptionRuleService).listHits(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getRuleType()).isEqualTo("LOW_STOCK");
        assertThat(queryCaptor.getValue().getSourceNo()).isEqualTo("W:11/P:22");
        assertThat(queryCaptor.getValue().getTicketId()).isEqualTo(9001L);
    }

    private static ExceptionRuleUpdateRequest updateRequest() {
        ExceptionRuleUpdateRequest request = new ExceptionRuleUpdateRequest();
        request.setThresholdValue(new BigDecimal("30"));
        request.setThresholdUnit("DAYS");
        request.setPriority("URGENT");
        request.setAssigneeUserId(9002L);
        request.setRemark("超过 30 天未结清");
        return request;
    }

    private static ExceptionRuleResponse rule() {
        return new ExceptionRuleResponse(
                1001L,
                "RECEIVABLE_OVERDUE_DEFAULT",
                "应收逾期",
                "RECEIVABLE_OVERDUE",
                "PAYMENT_OVERDUE",
                "URGENT",
                new BigDecimal("30"),
                "DAYS",
                true,
                9002L,
                60,
                LocalDateTime.of(2026, 6, 30, 11, 0),
                "超过 30 天未结清",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                "SUCCESS",
                2,
                1,
                null,
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }

    private static ExceptionRuleScanResultResponse scanResult() {
        return new ExceptionRuleScanResultResponse(
                1001L,
                "RECEIVABLE_OVERDUE_DEFAULT",
                "RECEIVABLE_OVERDUE",
                "SUCCESS",
                2,
                1,
                1,
                "扫描完成",
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }

    private static ExceptionRuleHitResponse hit() {
        return new ExceptionRuleHitResponse(
                2001L,
                1001L,
                "LOW_STOCK_DEFAULT",
                "LOW_STOCK",
                "LOW_STOCK",
                7001L,
                "W:11/P:22",
                "/inventory/alerts?warehouseId=11&productId=22",
                "LOW_STOCK:11:22",
                "库存低于安全线",
                "当前库存 3，安全库存 10",
                "3",
                "10",
                9001L,
                1,
                LocalDateTime.of(2026, 6, 30, 10, 0),
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }
}

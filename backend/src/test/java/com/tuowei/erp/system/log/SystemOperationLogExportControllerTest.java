package com.tuowei.erp.system.log;

import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogResponse;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemOperationLogExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemLogService systemLogService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void operationLogDetailRequiresSystemLogViewPermission() throws Exception {
        mockMvc.perform(get("/api/system/operation-logs/{id}", 9101L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(systemLogService);
    }

    @Test
    @WithErpUser(authorities = "system:log:view")
    void operationLogDetailDelegatesToService() throws Exception {
        when(systemLogService.getOperationLog(9101L)).thenReturn(operationLog());

        mockMvc.perform(get("/api/system/operation-logs/{id}", 9101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9101L))
                .andExpect(jsonPath("$.data.module").value("purchase"))
                .andExpect(jsonPath("$.data.operation").value("post"));

        verify(systemLogService).getOperationLog(9101L);
    }

    @Test
    @WithErpUser(authorities = "system:log:view")
    void operationLogExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("id,module,operation\r\n9101,purchase,post\r\n");
        when(systemLogService.exportOperationLogs(any(OperationLogPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/system/operation-logs/export")
                        .param("userId", "9701")
                        .param("username", "admin")
                        .param("module", "purchase")
                        .param("operation", "post")
                        .param("bizNo", "GR-001")
                        .param("result", "SUCCESS")
                        .param("operationTimeFrom", "2026-06-01T00:00:00")
                        .param("operationTimeTo", "2026-06-30T23:59:59"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("id,module,operation\r\n9101,purchase,post\r\n"))
                .andReturn();

        assertThat(dispatched.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''operation-logs.csv")
                .doesNotContain("filename=operation-logs.csv");

        ArgumentCaptor<OperationLogPageQuery> captor = ArgumentCaptor.forClass(OperationLogPageQuery.class);
        verify(systemLogService).exportOperationLogs(captor.capture());
        OperationLogPageQuery query = captor.getValue();
        assertThat(query.getUserId()).isEqualTo(9701L);
        assertThat(query.getUsername()).isEqualTo("admin");
        assertThat(query.getModule()).isEqualTo("purchase");
        assertThat(query.getOperation()).isEqualTo("post");
        assertThat(query.getBizNo()).isEqualTo("GR-001");
        assertThat(query.getResult()).isEqualTo("SUCCESS");
        assertThat(query.getOperationTimeFrom()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(query.getOperationTimeTo()).isEqualTo(LocalDateTime.of(2026, 6, 30, 23, 59, 59));
    }

    private OperationLogResponse operationLog() {
        return new OperationLogResponse(
                9101L,
                9701L,
                "admin",
                "purchase",
                "post",
                "GR-001",
                "SUCCESS",
                "posted",
                "POST",
                "/api/purchase/receipts/7001/post",
                LocalDateTime.of(2026, 6, 18, 10, 30)
        );
    }

}

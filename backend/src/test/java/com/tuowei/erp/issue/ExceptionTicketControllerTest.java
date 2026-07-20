package com.tuowei.erp.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketEventResponse;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExceptionTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExceptionTicketService exceptionTicketService;

    @Test
    @WithErpUser(authorities = "report:view")
    void listRequiresExceptionTicketViewPermission() throws Exception {
        mockMvc.perform(get("/api/exception-tickets"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionTicketService);
    }

    @Test
    @WithErpUser(authorities = "exception-ticket:view")
    void listBindsQueryAndReturnsPage() throws Exception {
        when(exceptionTicketService.list(any(ExceptionTicketPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(ticket())
        ));

        mockMvc.perform(get("/api/exception-tickets")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "库存")
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("category", "LOW_STOCK")
                        .param("assigneeUserId", "9002")
                        .param("sourceNo", "SO-001")
                        .param("overdueOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].ticketNo").value("ET-20260630-0001"))
                .andExpect(jsonPath("$.data.records[0].traceable").value(true))
                .andExpect(jsonPath("$.data.records[0].traceRoute").value("/reports/traces?keyword=SO-001"));

        ArgumentCaptor<ExceptionTicketPageQuery> queryCaptor = ArgumentCaptor.forClass(ExceptionTicketPageQuery.class);
        verify(exceptionTicketService).list(queryCaptor.capture());
        ExceptionTicketPageQuery query = queryCaptor.getValue();
        assertThat(query.getKeyword()).isEqualTo("库存");
        assertThat(query.getStatus()).isEqualTo("OPEN");
        assertThat(query.getPriority()).isEqualTo("HIGH");
        assertThat(query.getCategory()).isEqualTo("LOW_STOCK");
        assertThat(query.getAssigneeUserId()).isEqualTo(9002L);
        assertThat(query.getSourceNo()).isEqualTo("SO-001");
        assertThat(query.getOverdueOnly()).isTrue();
    }

    @Test
    @WithErpUser(authorities = "exception-ticket:view")
    void createRequiresManagePermission() throws Exception {
        mockMvc.perform(post("/api/exception-tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(exceptionTicketService);
    }

    @Test
    @WithErpUser(authorities = {"exception-ticket:view", "exception-ticket:manage"})
    void createBindsBodyAndReturnsTicket() throws Exception {
        when(exceptionTicketService.create(any(ExceptionTicketCreateRequest.class))).thenReturn(ticket());

        mockMvc.perform(post("/api/exception-tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketNo").value("ET-20260630-0001"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        ArgumentCaptor<ExceptionTicketCreateRequest> requestCaptor = ArgumentCaptor.forClass(ExceptionTicketCreateRequest.class);
        verify(exceptionTicketService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTitle()).isEqualTo("库存低于安全线");
        assertThat(requestCaptor.getValue().getAssigneeUserId()).isEqualTo(9002L);
    }

    @Test
    @WithErpUser(authorities = {"exception-ticket:view", "exception-ticket:manage"})
    void actionEndpointsDelegateToService() throws Exception {
        when(exceptionTicketService.assign(any(Long.class), any(ExceptionTicketAssignRequest.class))).thenReturn(ticket("OPEN"));
        when(exceptionTicketService.start(any(Long.class), any(ExceptionTicketActionRequest.class))).thenReturn(ticket("PROCESSING"));
        when(exceptionTicketService.resolve(any(Long.class), any(ExceptionTicketActionRequest.class))).thenReturn(ticket("RESOLVED"));
        when(exceptionTicketService.close(any(Long.class), any(ExceptionTicketActionRequest.class))).thenReturn(ticket("CLOSED"));

        mockMvc.perform(post("/api/exception-tickets/1001/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExceptionTicketAssignRequest(9003L, "转给仓库主管"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(post("/api/exception-tickets/1001/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExceptionTicketActionRequest("开始排查"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(post("/api/exception-tickets/1001/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExceptionTicketActionRequest("已补货"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mockMvc.perform(post("/api/exception-tickets/1001/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExceptionTicketActionRequest("确认关闭"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        verify(exceptionTicketService).assign(any(Long.class), any(ExceptionTicketAssignRequest.class));
        verify(exceptionTicketService).start(any(Long.class), any(ExceptionTicketActionRequest.class));
        verify(exceptionTicketService).resolve(any(Long.class), any(ExceptionTicketActionRequest.class));
        verify(exceptionTicketService).close(any(Long.class), any(ExceptionTicketActionRequest.class));
    }

    private static ExceptionTicketCreateRequest createRequest() {
        ExceptionTicketCreateRequest request = new ExceptionTicketCreateRequest();
        request.setCategory("LOW_STOCK");
        request.setPriority("HIGH");
        request.setTitle("库存低于安全线");
        request.setDescription("A 仓原材料库存不足");
        request.setSourceType("LOW_STOCK");
        request.setSourceId(7001L);
        request.setSourceNo("SO-001");
        request.setSourceRoute("/inventory/alerts");
        request.setAssigneeUserId(9002L);
        request.setDueTime(LocalDateTime.of(2026, 6, 30, 18, 0));
        return request;
    }

    private static ExceptionTicketResponse ticket() {
        return ticket("OPEN");
    }

    private static ExceptionTicketResponse ticket(String status) {
        return new ExceptionTicketResponse(
                1001L,
                "ET-20260630-0001",
                "LOW_STOCK",
                "HIGH",
                "库存低于安全线",
                "A 仓原材料库存不足",
                "LOW_STOCK",
                7001L,
                "SO-001",
                "/inventory/alerts",
                true,
                "SO-001",
                "/reports/traces?keyword=SO-001",
                status,
                9002L,
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null,
                null,
                null,
                9001L,
                LocalDateTime.of(2026, 6, 30, 10, 0),
                LocalDateTime.of(2026, 6, 30, 10, 0),
                List.of(new ExceptionTicketEventResponse(
                        2001L,
                        1001L,
                        "CREATE",
                        null,
                        "OPEN",
                        "创建异常工单",
                        9001L,
                        LocalDateTime.of(2026, 6, 30, 10, 0)
                ))
        );
    }
}

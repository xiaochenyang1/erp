package com.tuowei.erp.system.auth;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.auth.service.UserSessionService;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.auth.web.UserSessionResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class UserSessionControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void listRequiresUserSessionViewPermission() throws Exception {
        mockMvc.perform(get("/api/system/user-sessions")
                        .param("userId", "1001")
                        .param("status", "ACTIVE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(userSessionService);
    }

    @Test
    @WithErpUser(authorities = "system:user-session:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(userSessionService.list(any(UserSessionPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(response())
        ));

        mockMvc.perform(get("/api/system/user-sessions")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("userId", "1001")
                        .param("username", "demo")
                        .param("status", "ACTIVE")
                        .param("issuedAtFrom", "2026-01-01T08:00:00")
                        .param("issuedAtTo", "2026-01-31T18:30:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(9001))
                .andExpect(jsonPath("$.data.records[0].username").value("demo"));

        ArgumentCaptor<UserSessionPageQuery> queryCaptor = ArgumentCaptor.forClass(UserSessionPageQuery.class);
        verify(userSessionService).list(queryCaptor.capture());
        UserSessionPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getUserId()).isEqualTo(1001L);
        assertThat(query.getUsername()).isEqualTo("demo");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
        assertThat(query.getIssuedAtFrom()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
        assertThat(query.getIssuedAtTo()).isEqualTo(LocalDateTime.of(2026, 1, 31, 18, 30));
    }

    @Test
    @WithErpUser(authorities = "system:user-session:view")
    void revokeSessionRequiresRevokePermission() throws Exception {
        mockMvc.perform(post("/api/system/user-sessions/{id}/revoke", 9001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(userSessionService);
    }

    @Test
    @WithErpUser(authorities = "system:user-session:revoke")
    void revokeSessionDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/system/user-sessions/{id}/revoke", 9001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(userSessionService).revokeSession(9001L);
    }

    @Test
    @WithErpUser(authorities = "system:user-session:view")
    void revokeUserSessionsRequiresRevokePermission() throws Exception {
        mockMvc.perform(post("/api/system/users/{id}/sessions/revoke", 1001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(userSessionService);
    }

    @Test
    @WithErpUser(authorities = "system:user-session:revoke")
    void revokeUserSessionsDelegatesToService() throws Exception {
        mockMvc.perform(post("/api/system/users/{id}/sessions/revoke", 1001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(userSessionService).revokeAllForUser(1001L);
    }

    private static UserSessionResponse response() {
        return new UserSessionResponse(
                9001L,
                1001L,
                "demo",
                "演示用户",
                "ACTIVE",
                "127.0.0.1",
                "JUnit",
                LocalDateTime.of(2026, 1, 10, 9, 0),
                LocalDateTime.of(2026, 1, 10, 9, 30),
                LocalDateTime.of(2026, 1, 11, 9, 0),
                null
        );
    }
}

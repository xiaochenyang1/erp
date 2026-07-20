package com.tuowei.erp.system.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSessionManagementTest {

    private static final long USER_ID = 883001L;
    private static final long ADMIN_ID = 883002L;
    private static final long SESSION_MANAGEMENT_ROLE_ID = 883401L;
    private static final String USERNAME = "session_mgmt_user";
    private static final String ADMIN_USERNAME = "session_mgmt_admin";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String USER_AGENT = "SessionManagementTest/1.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        cleanup();
        seedUser(USER_ID, USERNAME);
        seedUser(ADMIN_ID, ADMIN_USERNAME);
        seedSessionManagementRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (883901, ?, ?, 0)",
                ADMIN_ID, SESSION_MANAGEMENT_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void adminCanListAndRevokeSingleUserSession() throws Exception {
        TokenPair userLogin = login(USERNAME);
        TokenPair adminLogin = login(ADMIN_USERNAME);

        MvcResult listResult = mockMvc.perform(get("/api/system/user-sessions")
                        .header("Authorization", "Bearer " + adminLogin.accessToken())
                        .param("userId", Long.toString(USER_ID))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data.records[0].username").value(USERNAME))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].loginIp").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].userAgent").value(USER_AGENT))
                .andReturn();

        long sessionId = objectMapper.readTree(listResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("records")
                .get(0)
                .path("id")
                .asLong();

        mockMvc.perform(post("/api/system/user-sessions/{id}/revoke", sessionId)
                        .header("Authorization", "Bearer " + adminLogin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(userLogin.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanRevokeAllSessionsForUserWithoutRevokingOwnSession() throws Exception {
        TokenPair firstUserLogin = login(USERNAME);
        TokenPair secondUserLogin = login(USERNAME);
        TokenPair adminLogin = login(ADMIN_USERNAME);

        mockMvc.perform(post("/api/system/users/{id}/sessions/revoke", USER_ID)
                        .header("Authorization", "Bearer " + adminLogin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstUserLogin.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(secondUserLogin.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(adminLogin.refreshToken())))
                .andExpect(status().isOk());
    }

    private TokenPair login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return new TokenPair(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private void seedUser(long id, String username) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'user session management test', 0, 0, 0)
                """, id, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
    }

    private void seedSessionManagementRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'SESSION_MANAGEMENT_TEST', '会话管理测试角色', 'ACTIVE', 0,
                        'user session management test', 0, 0, 0)
                """, SESSION_MANAGEMENT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (883902, ?, 5027, 0)",
                SESSION_MANAGEMENT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (883903, ?, 5028, 0)",
                SESSION_MANAGEMENT_ROLE_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?)", USER_ID, ADMIN_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?)", USERNAME, ADMIN_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?)", USER_ID, ADMIN_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", SESSION_MANAGEMENT_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", SESSION_MANAGEMENT_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?)", USER_ID, ADMIN_ID);
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }
}

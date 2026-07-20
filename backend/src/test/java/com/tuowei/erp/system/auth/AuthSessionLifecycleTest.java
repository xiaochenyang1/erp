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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionLifecycleTest {

    private static final long USER_ID = 882001L;
    private static final long ADMIN_ID = 882002L;
    private static final long RESET_PASSWORD_ROLE_ID = 882401L;
    private static final String USERNAME = "auth_session_user";
    private static final String ADMIN_USERNAME = "auth_session_admin";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String NEW_PASSWORD = "N3wP@ssw0rd!";
    private static final String RESET_PASSWORD = "ResetP@ss123";

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
        seedUser(USER_ID, USERNAME, PASSWORD);
        seedUser(ADMIN_ID, ADMIN_USERNAME, PASSWORD);
        seedResetPasswordRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (882901, ?, ?, 0)",
                ADMIN_ID, RESET_PASSWORD_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void loginReturnsRefreshTokenAndRefreshRotatesIt() throws Exception {
        TokenPair login = login(USERNAME, PASSWORD);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(login.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        TokenPair rotated = parseTokenPair(refreshed);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(rotated.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        TokenPair login = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(login.refreshToken())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void changePasswordRevokesAllUserRefreshTokens() throws Exception {
        TokenPair firstLogin = login(USERNAME, PASSWORD);
        TokenPair secondLogin = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + firstLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(secondLogin.refreshToken())))
                .andExpect(status().isUnauthorized());

        login(USERNAME, NEW_PASSWORD);
    }

    @Test
    void resetPasswordRevokesAllUserRefreshTokensAndAllowsNewPasswordLogin() throws Exception {
        TokenPair userLogin = login(USERNAME, PASSWORD);
        TokenPair adminLogin = login(ADMIN_USERNAME, PASSWORD);

        mockMvc.perform(post("/api/system/users/{id}/reset-password", USER_ID)
                        .header("Authorization", "Bearer " + adminLogin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"%s"}
                                """.formatted(RESET_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(userLogin.refreshToken())))
                .andExpect(status().isUnauthorized());

        login(USERNAME, RESET_PASSWORD);
    }

    private TokenPair login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        return parseTokenPair(result);
    }

    private TokenPair parseTokenPair(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return new TokenPair(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private void seedUser(long id, String username, String password) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'auth session lifecycle test', 0, 0, 0)
                """, id, username, passwordEncoder.encode(password), "EMP_" + id, username);
    }

    private void seedResetPasswordRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'RESET_PASSWORD_TEST', '重置密码测试角色', 'ACTIVE', 0,
                        'auth session lifecycle test', 0, 0, 0)
                """, RESET_PASSWORD_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (882902, ?, 5026, 0)",
                RESET_PASSWORD_ROLE_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?)", USER_ID, ADMIN_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?)", USERNAME, ADMIN_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?)", USER_ID, ADMIN_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", RESET_PASSWORD_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", RESET_PASSWORD_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?)", USER_ID, ADMIN_ID);
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }
}

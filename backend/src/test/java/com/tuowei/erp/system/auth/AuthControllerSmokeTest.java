package com.tuowei.erp.system.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerSmokeTest {

    private static final long LOGIN_USER_ID = 881001L;
    private static final String LOGIN_USERNAME = "smoke_login_881001";
    private static final String LOGIN_PASSWORD = "P@ssw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_refresh_token where user_id = ?", LOGIN_USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username = ?", LOGIN_USERNAME);
        jdbcTemplate.update("delete from sys_user where id = ?", LOGIN_USER_ID);
    }

    @Test
    void protectedApiReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/system/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        seedLoginUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"smoke_login_881001","password":"P@ssw0rd123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(LOGIN_USERNAME));
    }

    private void seedLoginUser() {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'EMP_SMOKE_881001', '登录冒烟用户', 3501, 3601,
                        'ACTIVE', 0, 'auth smoke test', 0, 0, 0)
                """, LOGIN_USER_ID, LOGIN_USERNAME, passwordEncoder.encode(LOGIN_PASSWORD));
    }
}

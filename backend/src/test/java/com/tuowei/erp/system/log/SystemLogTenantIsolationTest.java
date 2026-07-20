package com.tuowei.erp.system.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemLogTenantIsolationTest {

    private static final long COMPANY_ADMIN_ID = 895001L;
    private static final long OTHER_COMPANY_USER_ID = 895002L;
    private static final long SYSTEM_LOG_ROLE_ID = 895401L;
    private static final String COMPANY_ADMIN_USERNAME = "log_tenant_admin";
    private static final String OTHER_COMPANY_USERNAME = "log_tenant_other";
    private static final String PASSWORD = "P@ssw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SystemLogService systemLogService;

    @BeforeEach
    void setup() {
        cleanup();
        seedUser(COMPANY_ADMIN_ID, COMPANY_ADMIN_USERNAME, 1L, 1L);
        seedUser(OTHER_COMPANY_USER_ID, OTHER_COMPANY_USERNAME, 2L, 1L);
        seedSystemLogRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (895901, ?, ?, 0)",
                COMPANY_ADMIN_ID, SYSTEM_LOG_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void loginLogListOnlyShowsCurrentCompanyLogs() throws Exception {
        login(OTHER_COMPANY_USERNAME);
        String companyToken = login(COMPANY_ADMIN_USERNAME);

        mockMvc.perform(get("/api/system/login-logs")
                        .header("Authorization", "Bearer " + companyToken)
                        .param("username", COMPANY_ADMIN_USERNAME)
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(COMPANY_ADMIN_USERNAME));

        mockMvc.perform(get("/api/system/login-logs")
                        .header("Authorization", "Bearer " + companyToken)
                        .param("username", OTHER_COMPANY_USERNAME)
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void operationAndAuditLogListsOnlyShowCurrentCompanyLogs() {
        systemLogService.recordOperation(
                principal(COMPANY_ADMIN_ID, 1L, COMPANY_ADMIN_USERNAME),
                "tenant-log-test",
                "company-operation",
                "LOG-TENANT-1",
                "SUCCESS",
                "company operation",
                null
        );
        systemLogService.recordOperation(
                principal(OTHER_COMPANY_USER_ID, 2L, OTHER_COMPANY_USERNAME),
                "tenant-log-test",
                "other-operation",
                "LOG-TENANT-2",
                "SUCCESS",
                "other operation",
                null
        );

        TestSecurityContexts.useUser(
                COMPANY_ADMIN_ID,
                1L,
                1L,
                3501L,
                3601L,
                COMPANY_ADMIN_USERNAME,
                COMPANY_ADMIN_USERNAME,
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
        systemLogService.recordAudit(
                "TENANT_LOG_TEST",
                "TENANT_LOG_TEST",
                895101L,
                "AUDIT-TENANT-1",
                "CREATE",
                COMPANY_ADMIN_ID,
                COMPANY_ADMIN_USERNAME,
                null,
                "company audit",
                LocalDateTime.now()
        );

        TestSecurityContexts.useUser(
                OTHER_COMPANY_USER_ID,
                2L,
                1L,
                3501L,
                3601L,
                OTHER_COMPANY_USERNAME,
                OTHER_COMPANY_USERNAME,
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
        systemLogService.recordAudit(
                "TENANT_LOG_TEST",
                "TENANT_LOG_TEST",
                895102L,
                "AUDIT-TENANT-2",
                "CREATE",
                OTHER_COMPANY_USER_ID,
                OTHER_COMPANY_USERNAME,
                null,
                "other audit",
                LocalDateTime.now()
        );

        TestSecurityContexts.useUser(
                COMPANY_ADMIN_ID,
                1L,
                1L,
                3501L,
                3601L,
                COMPANY_ADMIN_USERNAME,
                COMPANY_ADMIN_USERNAME,
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );

        OperationLogPageQuery operationQuery = new OperationLogPageQuery();
        operationQuery.setModule("tenant-log-test");
        assertThat(systemLogService.listOperationLogs(operationQuery).records())
                .extracting(record -> record.bizNo())
                .containsExactly("LOG-TENANT-1");

        AuditLogPageQuery auditQuery = new AuditLogPageQuery();
        auditQuery.setBusinessType("TENANT_LOG_TEST");
        assertThat(systemLogService.listAuditLogs(auditQuery).records())
                .extracting(record -> record.businessNo())
                .containsExactly("AUDIT-TENANT-1");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("accessToken").asText();
    }

    private void seedUser(long id, String username, long companyId, long accountBookId) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, ?, ?, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'system log tenant isolation test', 0, 0, 0)
                """, id, companyId, accountBookId, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
    }

    private void seedSystemLogRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'SYSTEM_LOG_TEST', '系统日志测试角色', 'ACTIVE', 0,
                        'system log tenant isolation test', 0, 0, 0)
                """, SYSTEM_LOG_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (895902, ?, 5008, 0)",
                SYSTEM_LOG_ROLE_ID);
    }

    private ErpPrincipal principal(long userId, long companyId, String username) {
        return new ErpPrincipal(
                userId,
                companyId,
                1L,
                3501L,
                3601L,
                username,
                username,
                "N/A",
                Set.of(PermissionCodes.SYSTEM_LOG_VIEW),
                DataScopeSnapshot.all()
        );
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_audit_log where business_no in ('AUDIT-TENANT-1', 'AUDIT-TENANT-2')");
        jdbcTemplate.update("delete from sys_operation_log where biz_no in ('LOG-TENANT-1', 'LOG-TENANT-2')");
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?)", COMPANY_ADMIN_ID, OTHER_COMPANY_USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?)", COMPANY_ADMIN_USERNAME, OTHER_COMPANY_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?)", COMPANY_ADMIN_ID, OTHER_COMPANY_USER_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", SYSTEM_LOG_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", SYSTEM_LOG_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?)", COMPANY_ADMIN_ID, OTHER_COMPANY_USER_ID);
    }
}

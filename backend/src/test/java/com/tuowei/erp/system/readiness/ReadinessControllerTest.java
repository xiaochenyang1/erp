package com.tuowei.erp.system.readiness;

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
class ReadinessControllerTest {

    private static final long ADMIN_ID = 889001L;
    private static final long OTHER_TENANT_ADMIN_ID = 889002L;
    private static final long NO_PERMISSION_USER_ID = 889003L;
    private static final long READINESS_ROLE_ID = 889102L;
    private static final long OTHER_TENANT_ROLE_ID = 889101L;
    private static final String ADMIN_USERNAME = "readiness_admin";
    private static final String OTHER_TENANT_USERNAME = "readiness_other_tenant";
    private static final String NO_PERMISSION_USERNAME = "readiness_no_permission";
    private static final String PASSWORD = "P@ssw0rd123";

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
        seedUser(ADMIN_ID, ADMIN_USERNAME, 1L, 1L);
        seedUser(OTHER_TENANT_ADMIN_ID, OTHER_TENANT_USERNAME, 2L, 1L);
        seedUser(NO_PERMISSION_USER_ID, NO_PERMISSION_USERNAME, 1L, 1L);
        seedReadinessRole(READINESS_ROLE_ID, 1L, "READINESS_TEST_ADMIN", 889904L);
        seedReadinessRole(OTHER_TENANT_ROLE_ID, 2L, "READINESS_TEST_OTHER_ADMIN", 889914L);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (889901, ?, ?, 0)",
                ADMIN_ID, READINESS_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (889902, ?, ?, 0)",
                OTHER_TENANT_ADMIN_ID, OTHER_TENANT_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void createRunAddItemEvidenceAndQueryDetail() throws Exception {
        String token = login(ADMIN_USERNAME);
        long runId = createRun(token);
        long itemId = addItem(token, runId, "AUTH_SMOKE", "登录与权限冒烟", "AUTH", "P0");

        mockMvc.perform(post("/api/system/readiness/items/{itemId}/evidence", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceType":"API",
                                  "requestMethod":"GET",
                                  "requestUri":"/api/system/profile",
                                  "httpStatus":200,
                                  "businessType":"AUTH",
                                  "businessNo":"AUTH-SMOKE-001",
                                  "summary":"受保护接口返回 200",
                                  "detail":"profile endpoint ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(itemId))
                .andExpect(jsonPath("$.data.evidenceType").value("API"))
                .andExpect(jsonPath("$.data.summary").value("受保护接口返回 200"));

        mockMvc.perform(get("/api/system/readiness/runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run.id").value(runId))
                .andExpect(jsonPath("$.data.run.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(itemId))
                .andExpect(jsonPath("$.data.items[0].evidence.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].evidence[0].summary").value("受保护接口返回 200"));

        mockMvc.perform(get("/api/system/readiness/runs")
                        .header("Authorization", "Bearer " + token)
                        .param("releaseCommit", "abc1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(runId));
    }

    @Test
    void createRunCanGenerateDefaultPreproductionChecklist() throws Exception {
        String token = login(ADMIN_USERNAME);

        MvcResult result = mockMvc.perform(post("/api/system/readiness/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "releaseCommit":"def5678",
                                  "releaseVersion":"1.0.0-rc2",
                                  "environment":"preprod",
                                  "databaseInstance":"mysql-preprod",
                                  "redisInstance":"redis-preprod",
                                  "dockerProfile":"core",
                                  "generateDefaultItems":true,
                                  "remark":"default checklist test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();
        long runId = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/system/readiness/runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(12))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("RELEASE_GATE"))
                .andExpect(jsonPath("$.data.items[0].priority").value("P0"))
                .andExpect(jsonPath("$.data.items[10].itemCode").value("BACKUP_ROLLBACK"))
                .andExpect(jsonPath("$.data.items[11].itemCode").value("PREPROD_APPROVAL_GATE"))
                .andExpect(jsonPath("$.data.items[11].priority").value("P0"));

        mockMvc.perform(post("/api/system/readiness/runs/{id}/decision", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision":"GO",
                                  "status":"PASSED",
                                  "decisionComment":"准备发布"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("存在未通过或未执行的 P0/P1 验收项，不能标记发布通过"));
    }

    @Test
    void createRunCanGenerateDefaultChecklistAndRecordPreflightEvidence() throws Exception {
        String token = login(ADMIN_USERNAME);

        MvcResult result = mockMvc.perform(post("/api/system/readiness/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "releaseCommit":"ghi9012",
                                  "releaseVersion":"1.0.0-rc3",
                                  "environment":"preprod",
                                  "databaseInstance":"mysql-preprod",
                                  "redisInstance":"redis-preprod",
                                  "dockerProfile":"core",
                                  "generateDefaultItems":true,
                                  "recordPreflightEvidence":true,
                                  "remark":"default checklist with preflight"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();
        long runId = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/system/readiness/runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(13))
                .andExpect(jsonPath("$.data.items[?(@.itemCode=='MIGRATION_PREFLIGHT')].status").value("PASSED"))
                .andExpect(jsonPath("$.data.items[?(@.itemCode=='MIGRATION_PREFLIGHT')].evidence[0].summary").value("迁移前健康检查：PASS"));
    }

    @Test
    void blockingP0FailurePreventsGoDecision() throws Exception {
        String token = login(ADMIN_USERNAME);
        long runId = createRun(token);
        long itemId = addItem(token, runId, "PURCHASE_TO_PAYMENT", "采购到付款", "PURCHASE", "P0");

        mockMvc.perform(post("/api/system/readiness/items/{itemId}/result", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"FAILED",
                                  "actualResult":"付款核销失败",
                                  "failureReason":"应付余额未正确回写"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(post("/api/system/readiness/runs/{id}/decision", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision":"GO",
                                  "status":"PASSED",
                                  "decisionComment":"准备发布"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("存在未通过或未执行的 P0/P1 验收项，不能标记发布通过"));
    }

    @Test
    void noGoRequiresDecisionCommentAndClosedRunRejectsEvidence() throws Exception {
        String token = login(ADMIN_USERNAME);
        long runId = createRun(token);
        long itemId = addItem(token, runId, "DEPLOYMENT_HEALTH", "部署健康检查", "DEPLOYMENT", "P1");

        mockMvc.perform(post("/api/system/readiness/runs/{id}/decision", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision":"NO_GO",
                                  "status":"NO_GO"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No-Go 决策说明不能为空"));

        mockMvc.perform(post("/api/system/readiness/runs/{id}/decision", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision":"NO_GO",
                                  "status":"NO_GO",
                                  "decisionComment":"Docker 健康检查失败"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_GO"))
                .andExpect(jsonPath("$.data.decision").value("NO_GO"));

        mockMvc.perform(post("/api/system/readiness/items/{itemId}/evidence", itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceType":"LOG",
                                  "summary":"发布后补日志"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("已关闭的验收运行单不能修改"));
    }

    @Test
    void tenantIsolationPreventsCrossCompanyAccess() throws Exception {
        String token = login(ADMIN_USERNAME);
        long runId = createRun(token);
        String otherToken = login(OTHER_TENANT_USERNAME);

        mockMvc.perform(get("/api/system/readiness/runs/{id}", runId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验收运行单不存在"));
    }

    @Test
    void userWithoutReadinessPermissionGetsForbidden() throws Exception {
        String token = login(NO_PERMISSION_USERNAME);

        mockMvc.perform(get("/api/system/readiness/runs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightReturnsPassWhenNoMigrationRisksExist() throws Exception {
        String token = login(ADMIN_USERNAME);

        mockMvc.perform(get("/api/system/readiness/preflight")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("PASS"))
                .andExpect(jsonPath("$.data.checkedAt").exists())
                .andExpect(jsonPath("$.data.items[?(@.code=='NEGATIVE_INVENTORY')].status").value("PASS"))
                .andExpect(jsonPath("$.data.items[?(@.code=='RECEIVABLE_SETTLEMENT_RANGE')].severity").value("P0"));
    }

    @Test
    void preflightFailsAndReturnsSampleWhenNegativeInventoryExists() throws Exception {
        String token = login(ADMIN_USERNAME);
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, qty_on_hand, amount_on_hand,
                 created_by, updated_by, version)
                values (889501, 1, 1, 889401, 889301, -1.0000, 0, 0, 0, 0)
                """);

        mockMvc.perform(get("/api/system/readiness/preflight")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("FAIL"))
                .andExpect(jsonPath("$.data.items[?(@.code=='NEGATIVE_INVENTORY')].status").value("FAIL"))
                .andExpect(jsonPath("$.data.items[?(@.code=='NEGATIVE_INVENTORY')].count").value(1))
                .andExpect(jsonPath("$.data.items[?(@.code=='NEGATIVE_INVENTORY')].sample[0]").exists());
    }

    @Test
    void preflightEvidencePersistsResultIntoReadinessRun() throws Exception {
        String token = login(ADMIN_USERNAME);
        long runId = createRun(token);
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, product_id, qty_on_hand, amount_on_hand,
                 created_by, updated_by, version)
                values (889501, 1, 1, 889401, 889301, -1.0000, 0, 0, 0, 0)
                """);

        mockMvc.perform(post("/api/system/readiness/runs/{id}/preflight-evidence", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").value("FAIL"))
                .andExpect(jsonPath("$.data.items[?(@.code=='NEGATIVE_INVENTORY')].status").value("FAIL"));

        mockMvc.perform(get("/api/system/readiness/runs/{id}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.itemCode=='MIGRATION_PREFLIGHT')].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[?(@.itemCode=='MIGRATION_PREFLIGHT')].evidence[0].evidenceType").value("API"))
                .andExpect(jsonPath("$.data.items[?(@.itemCode=='MIGRATION_PREFLIGHT')].evidence[0].summary").value("迁移前健康检查：FAIL"));
    }

    private long createRun(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/system/readiness/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "releaseCommit":"abc1234",
                                  "releaseVersion":"1.0.0-rc1",
                                  "environment":"preprod",
                                  "databaseInstance":"mysql-preprod",
                                  "redisInstance":"redis-preprod",
                                  "dockerProfile":"core",
                                  "remark":"readiness test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseCommit").value("abc1234"))
                .andExpect(jsonPath("$.data.environment").value("PREPROD"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.decision").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();
    }

    private long addItem(String token, long runId, String itemCode, String itemName, String category, String priority) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/system/readiness/runs/{id}/items", runId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemCode":"%s",
                                  "itemName":"%s",
                                  "category":"%s",
                                  "priority":"%s",
                                  "expectedResult":"验收通过"
                                }
                                """.formatted(itemCode, itemName, category, priority)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCode").value(itemCode))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();
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
                        'ACTIVE', 0, 'readiness controller test', 0, 0, 0)
                """, id, companyId, accountBookId, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
    }

    private void seedReadinessRole(long roleId, long companyId, String roleCode, long roleMenuIdBase) {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, ?, 1, ?, '验收测试管理员', 'ACTIVE', 0, 'readiness controller test', 0, 0, 0)
                """, roleId, companyId, roleCode);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (?, ?, 5091, 0)",
                roleMenuIdBase, roleId);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (?, ?, 5092, 0)",
                roleMenuIdBase + 1, roleId);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (?, ?, 5093, 0)",
                roleMenuIdBase + 2, roleId);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from inv_balance where id = 889501");
        jdbcTemplate.update("delete from sys_readiness_evidence where company_id in (1, 2)");
        jdbcTemplate.update("delete from sys_readiness_item where company_id in (1, 2)");
        jdbcTemplate.update("delete from sys_readiness_run where company_id in (1, 2)");
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?, ?)", ADMIN_ID, OTHER_TENANT_ADMIN_ID, NO_PERMISSION_USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?, ?)", ADMIN_USERNAME, OTHER_TENANT_USERNAME, NO_PERMISSION_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?, ?)", ADMIN_ID, OTHER_TENANT_ADMIN_ID, NO_PERMISSION_USER_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id in (?, ?)", READINESS_ROLE_ID, OTHER_TENANT_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id in (?, ?)", READINESS_ROLE_ID, OTHER_TENANT_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?, ?)", ADMIN_ID, OTHER_TENANT_ADMIN_ID, NO_PERMISSION_USER_ID);
    }
}

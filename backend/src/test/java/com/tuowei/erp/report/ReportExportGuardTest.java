package com.tuowei.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.testsupport.WithErpUser;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "erp.report.max-export-rows=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportGuardTest {

    private static final String REPORT_VIEW = "report:view";
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 5, 20);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 20, 9, 0);
    private static final long JWT_USER_ID = 94981L;
    private static final long JWT_ROLE_ID = 94982L;
    private static final String JWT_USERNAME = "report_export_async";
    private static final String JWT_PASSWORD = "P@ssw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from pur_order where id between 94900 and 94999");
        jdbcTemplate.update("delete from sys_refresh_token where user_id = ?", JWT_USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username = ?", JWT_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", JWT_USER_ID);
        jdbcTemplate.update("delete from sys_role_data_scope where role_id = ?", JWT_ROLE_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", JWT_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", JWT_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id = ?", JWT_USER_ID);
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void exportRejectsResultSetAboveConfiguredLimit() throws Exception {
        seedPurchaseOrder(94901L, "PO-RPT-EXPORT-TOO-MANY-1", 94901L);
        seedPurchaseOrder(94902L, "PO-RPT-EXPORT-TOO-MANY-2", 94901L);

        mockMvc.perform(get("/api/reports/purchase-orders/export")
                        .param("supplierId", "94901"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("导出结果超过1行，请缩小筛选范围后重试"));
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void exportAllowsResultSetAtConfiguredLimit() throws Exception {
        seedPurchaseOrder(94911L, "PO-RPT-EXPORT-ONE", 94902L);

        MvcResult result = mockMvc.perform(get("/api/reports/purchase-orders/export")
                        .param("supplierId", "94902"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PO-RPT-EXPORT-ONE")));
    }

    @Test
    void exportKeepsJwtAuthenticationDuringAsyncDispatch() throws Exception {
        seedReportUser();
        seedPurchaseOrder(94921L, "PO-RPT-JWT-ASYNC", 94903L);
        String token = login();

        MvcResult result = mockMvc.perform(get("/api/reports/purchase-orders/export")
                        .header("Authorization", "Bearer " + token)
                        .param("supplierId", "94903"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PO-RPT-JWT-ASYNC")));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(JWT_USERNAME, JWT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("accessToken").asText();
    }

    private void seedReportUser() {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'report export async test', 0, 0, 0)
                """, JWT_USER_ID, JWT_USERNAME, passwordEncoder.encode(JWT_PASSWORD), "EMP_" + JWT_USER_ID, JWT_USERNAME);
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'REPORT_EXPORT_ASYNC_TEST', '报表导出异步测试角色', 'ACTIVE', 0,
                        'report export async test', 0, 0, 0)
                """, JWT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (94983, ?, 5021, 0)",
                JWT_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (94984, ?, ?, 0)",
                JWT_USER_ID, JWT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_data_scope (id, role_id, scope_type, warehouse_id, created_by) values (94985, ?, 'ALL', null, 0)",
                JWT_ROLE_ID);
    }

    private void seedPurchaseOrder(long id, String orderNo, long supplierId) {
        jdbcTemplate.update("""
                insert into pur_order
                (id, company_id, account_book_id, order_no, supplier_id, order_date, delivery_date, status,
                 approval_status, receipt_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, 'APPROVED',
                        'APPROVED', 'NOT_RECEIVED', ?, ?, ?,
                        0, 'report export guard test', 94900, ?, 94900, ?, 0)
                """,
                id,
                orderNo,
                supplierId,
                ORDER_DATE,
                ORDER_DATE,
                new BigDecimal("1.0000"),
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                NOW,
                NOW);
    }
}

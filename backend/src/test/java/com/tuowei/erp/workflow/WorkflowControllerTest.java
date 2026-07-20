package com.tuowei.erp.workflow;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowControllerTest {

    private static final long SUBMITTER_ID = 888001L;
    private static final long APPROVER_ID = 888002L;
    private static final long VIEW_ONLY_SUBMITTER_ID = 888003L;
    private static final long WORKFLOW_WITHDRAW_ROLE_ID = 888402L;
    private static final long WORKFLOW_VIEW_ONLY_ROLE_ID = 888401L;
    private static final long INSTANCE_ID = 888101L;
    private static final long TASK_ID = 888102L;
    private static final long SUBMIT_RECORD_ID = 888103L;
    private static final long NOTIFICATION_ID = 888201L;
    private static final long RECIPIENT_ID = 888202L;
    private static final long BUSINESS_ID = 888301L;
    private static final String BUSINESS_NO = "WF-CTRL-001";
    private static final String USERNAME = "workflow_controller_submitter";
    private static final String APPROVER_USERNAME = "workflow_controller_approver";
    private static final String VIEW_ONLY_USERNAME = "workflow_controller_view_only";
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
        seedUser(SUBMITTER_ID, USERNAME);
        seedUser(APPROVER_ID, APPROVER_USERNAME);
        seedUser(VIEW_ONLY_SUBMITTER_ID, VIEW_ONLY_USERNAME);
        seedWorkflowWithdrawRole();
        seedWorkflowViewOnlyRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (888901, ?, ?, 0)",
                SUBMITTER_ID, WORKFLOW_WITHDRAW_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (888902, ?, ?, 0)",
                VIEW_ONLY_SUBMITTER_ID, WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (888903, ?, ?, 0)",
                APPROVER_ID, WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (888904, ?, ?, 0)",
                SUBMITTER_ID, WORKFLOW_VIEW_ONLY_ROLE_ID);
        seedActiveWorkflow();
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void submitterCanWithdrawWorkflowThroughApi() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/workflow/{businessType}/{businessId}/withdraw", "SALES_ORDER", BUSINESS_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"api withdraw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        String instanceStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_instance
                where id = ?
                """, String.class, INSTANCE_ID);
        String taskStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_task
                where id = ?
                """, String.class, TASK_ID);
        Integer withdrawRecordCount = jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_record
                where instance_id = ?
                  and action = 'WITHDRAW'
                  and operator_user_id = ?
                  and comment = 'api withdraw'
                """, Integer.class, INSTANCE_ID, SUBMITTER_ID);
        String recipientStatus = jdbcTemplate.queryForObject("""
                select status
                from sys_notification_recipient
                where id = ?
                """, String.class, RECIPIENT_ID);
        Integer readFlag = jdbcTemplate.queryForObject("""
                select read_flag
                from sys_notification_recipient
                where id = ?
                """, Integer.class, RECIPIENT_ID);

        org.assertj.core.api.Assertions.assertThat(instanceStatus).isEqualTo("WITHDRAWN");
        org.assertj.core.api.Assertions.assertThat(taskStatus).isEqualTo("CANCELLED");
        org.assertj.core.api.Assertions.assertThat(withdrawRecordCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(recipientStatus).isEqualTo("CLOSED");
        org.assertj.core.api.Assertions.assertThat(readFlag).isEqualTo(1);
    }

    @Test
    void workflowViewOnlySubmitterCannotWithdrawThroughApi() throws Exception {
        jdbcTemplate.update("""
                update wf_approval_instance
                set submit_user_id = ?, created_by = ?, updated_by = ?
                where id = ?
                """, VIEW_ONLY_SUBMITTER_ID, VIEW_ONLY_SUBMITTER_ID, VIEW_ONLY_SUBMITTER_ID, INSTANCE_ID);
        String token = login(VIEW_ONLY_USERNAME);

        mockMvc.perform(post("/api/workflow/{businessType}/{businessId}/withdraw", "SALES_ORDER", BUSINESS_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"view only withdraw"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));
    }

    @Test
    void workflowTaskDetailCanBeQueriedThroughApi() throws Exception {
        String token = login(APPROVER_USERNAME);

        mockMvc.perform(get("/api/workflow/tasks/{id}", TASK_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(TASK_ID)))
                .andExpect(jsonPath("$.data.businessType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.data.businessId").value(String.valueOf(BUSINESS_ID)))
                .andExpect(jsonPath("$.data.businessNo").value(BUSINESS_NO))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void approverCanApproveWorkflowTaskThroughApi() throws Exception {
        String token = login(APPROVER_USERNAME);

        mockMvc.perform(post("/api/workflow/tasks/{id}/approve", TASK_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"task api approve"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        String instanceStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_instance
                where id = ?
                """, String.class, INSTANCE_ID);
        String taskStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_task
                where id = ?
                """, String.class, TASK_ID);
        Long approverUserId = jdbcTemplate.queryForObject("""
                select approver_user_id
                from wf_approval_task
                where id = ?
                """, Long.class, TASK_ID);
        Integer approveRecordCount = jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_record
                where instance_id = ?
                  and action = 'APPROVE'
                  and operator_user_id = ?
                  and comment = 'task api approve'
                """, Integer.class, INSTANCE_ID, APPROVER_ID);
        String recipientStatus = jdbcTemplate.queryForObject("""
                select status
                from sys_notification_recipient
                where id = ?
                """, String.class, RECIPIENT_ID);
        String orderStatus = jdbcTemplate.queryForObject("""
                select status
                from sal_order
                where id = ?
                """, String.class, BUSINESS_ID);
        String orderApprovalStatus = jdbcTemplate.queryForObject("""
                select approval_status
                from sal_order
                where id = ?
                """, String.class, BUSINESS_ID);

        org.assertj.core.api.Assertions.assertThat(instanceStatus).isEqualTo("APPROVED");
        org.assertj.core.api.Assertions.assertThat(taskStatus).isEqualTo("APPROVED");
        org.assertj.core.api.Assertions.assertThat(approverUserId).isEqualTo(APPROVER_ID);
        org.assertj.core.api.Assertions.assertThat(approveRecordCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(recipientStatus).isEqualTo("CLOSED");
        org.assertj.core.api.Assertions.assertThat(orderStatus).isEqualTo("APPROVED");
        org.assertj.core.api.Assertions.assertThat(orderApprovalStatus).isEqualTo("APPROVED");
    }

    @Test
    void submitterCannotApproveOwnWorkflowTaskThroughApi() throws Exception {
        String token = login(USERNAME);

        mockMvc.perform(post("/api/workflow/tasks/{id}/approve", TASK_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"self approve"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    private String login() throws Exception {
        return login(USERNAME);
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

    private void seedActiveWorkflow() {
        jdbcTemplate.update("""
                insert into sal_order
                (id, company_id, account_book_id, order_no, customer_id, warehouse_id, order_date, delivery_date,
                 status, approval_status, delivery_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 0, 0, current_date, current_date,
                        'SUBMITTED', 'IN_APPROVAL', 'NOT_DELIVERED', 0, 0, 0,
                        0, 'workflow controller test', ?, ?, 0)
                """, BUSINESS_ID, BUSINESS_NO, SUBMITTER_ID, SUBMITTER_ID);
        jdbcTemplate.update("""
                insert into wf_approval_instance
                (id, company_id, account_book_id, business_type, business_id, business_no, title,
                 status, submit_user_id, submit_time, deleted_flag, created_by, updated_by, version)
                values (?, 1, 1, 'SALES_ORDER', ?, ?, ?, 'IN_APPROVAL', ?, current_timestamp, 0, ?, ?, 0)
                """, INSTANCE_ID, BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO,
                SUBMITTER_ID, SUBMITTER_ID, SUBMITTER_ID);
        jdbcTemplate.update("""
                insert into wf_approval_task
                (id, company_id, account_book_id, instance_id, business_type, business_id, business_no, title,
                 status, created_by, updated_by, version)
                values (?, 1, 1, ?, 'SALES_ORDER', ?, ?, ?, 'PENDING', ?, ?, 0)
                """, TASK_ID, INSTANCE_ID, BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO,
                SUBMITTER_ID, SUBMITTER_ID);
        jdbcTemplate.update("""
                insert into wf_approval_record
                (id, company_id, account_book_id, instance_id, business_type, business_id, business_no,
                 action, operator_user_id, comment, action_time, created_by, updated_by, version)
                values (?, 1, 1, ?, 'SALES_ORDER', ?, ?, 'SUBMIT', ?, 'submit', current_timestamp, ?, ?, 0)
                """, SUBMIT_RECORD_ID, INSTANCE_ID, BUSINESS_ID, BUSINESS_NO,
                SUBMITTER_ID, SUBMITTER_ID, SUBMITTER_ID);
        jdbcTemplate.update("""
                insert into sys_notification
                (id, company_id, account_book_id, category, notification_type, title, content,
                 business_type, business_id, business_no, target_url, status, deleted_flag, created_by, updated_by, version)
                values (?, 1, 1, 'TODO', 'WORKFLOW_APPROVAL_PENDING', ?, ?,
                        'SALES_ORDER', ?, ?, concat('/workflow/tasks?businessType=SALES_ORDER&businessId=', ?), 'ACTIVE', 0, ?, ?, 0)
                """, NOTIFICATION_ID, "待审批：" + BUSINESS_NO, "单据 " + BUSINESS_NO + " 已提交审批",
                BUSINESS_ID, BUSINESS_NO, BUSINESS_ID, SUBMITTER_ID, SUBMITTER_ID);
        jdbcTemplate.update("""
                insert into sys_notification_recipient
                (id, company_id, notification_id, recipient_user_id, read_flag, status, created_by, updated_by, version)
                values (?, 1, ?, ?, 0, 'ACTIVE', ?, ?, 0)
                """, RECIPIENT_ID, NOTIFICATION_ID, APPROVER_ID, SUBMITTER_ID, SUBMITTER_ID);
    }

    private void seedWorkflowViewOnlyRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'WORKFLOW_VIEW_ONLY_TEST', '审批查看测试角色', 'ACTIVE', 0,
                        'workflow controller permission test', 0, 0, 0)
                """, WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (888911, ?, 5011, 0)",
                WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (888912, ?, 5012, 0)",
                WORKFLOW_VIEW_ONLY_ROLE_ID);
        // V102 收口后审批/驳回需独立写权限码:5322=workflow:approve,5323=workflow:reject
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (888914, ?, 5322, 0)",
                WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (888915, ?, 5323, 0)",
                WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_data_scope (id, role_id, scope_type, warehouse_id, created_by) values (888921, ?, 'ALL', null, 0)",
                WORKFLOW_VIEW_ONLY_ROLE_ID);
    }

    private void seedWorkflowWithdrawRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'WORKFLOW_WITHDRAW_TEST', '审批撤回测试角色', 'ACTIVE', 0,
                        'workflow controller permission test', 0, 0, 0)
                """, WORKFLOW_WITHDRAW_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (888913, ?, 5066, 0)",
                WORKFLOW_WITHDRAW_ROLE_ID);
    }

    private void seedUser(long id, String username) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'workflow controller test', 0, 0, 0)
                """, id, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_notification_recipient where id = ?", RECIPIENT_ID);
        jdbcTemplate.update("delete from sys_notification where id = ?", NOTIFICATION_ID);
        jdbcTemplate.update("delete from wf_approval_record where instance_id = ?", INSTANCE_ID);
        jdbcTemplate.update("delete from wf_approval_task where instance_id = ?", INSTANCE_ID);
        jdbcTemplate.update("delete from wf_approval_instance where id = ?", INSTANCE_ID);
        jdbcTemplate.update("delete from sal_order where id = ?", BUSINESS_ID);
        jdbcTemplate.update("delete from sys_audit_log where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, VIEW_ONLY_SUBMITTER_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?, ?)",
                USERNAME, APPROVER_USERNAME, VIEW_ONLY_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, VIEW_ONLY_SUBMITTER_ID);
        jdbcTemplate.update("delete from sys_role_data_scope where role_id in (?, ?)",
                WORKFLOW_WITHDRAW_ROLE_ID, WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", WORKFLOW_WITHDRAW_ROLE_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", WORKFLOW_WITHDRAW_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", WORKFLOW_VIEW_ONLY_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, VIEW_ONLY_SUBMITTER_ID);
    }
}

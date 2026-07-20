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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApprovalConfigControllerTest {

    private static final long USER_ID = 887001L;
    private static final long WORKFLOW_CONFIG_ROLE_ID = 887401L;
    private static final String USERNAME = "workflow_config_admin";
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
        seedUser();
        seedWorkflowConfigRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (887901, ?, ?, 0)",
                USER_ID, WORKFLOW_CONFIG_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void workflowConfigCanBeSavedAndReadBack() throws Exception {
        String token = login();

        mockMvc.perform(put("/api/workflow/configs/{businessType}", "SALES_ORDER")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configName": "销售订单审批配置",
                                  "status": "ACTIVE",
                                  "remark": "controller test",
                                  "nodes": [
                                    {
                                      "nodeName": "一级审批",
                                      "nodeOrder": 1,
                                      "approvalMode": "ANY",
                                      "approvers": [
                                        {"approverType": "ROLE", "approverId": 3002}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.data.configName").value("销售订单审批配置"))
                .andExpect(jsonPath("$.data.nodes[0].approvers[0].approverType").value("ROLE"))
                .andExpect(jsonPath("$.data.nodes[0].approvers[0].approverId").value(3002));

        mockMvc.perform(get("/api/workflow/configs/{businessType}", "SALES_ORDER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("SALES_ORDER"))
                .andExpect(jsonPath("$.data.nodes[0].nodeName").value("一级审批"))
                .andExpect(jsonPath("$.data.nodes[0].approvalMode").value("ANY"));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("accessToken").asText();
    }

    private void seedUser() {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'workflow config controller test', 0, 0, 0)
                """, USER_ID, USERNAME, passwordEncoder.encode(PASSWORD), "EMP_" + USER_ID, USERNAME);
    }

    private void seedWorkflowConfigRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'WORKFLOW_CONFIG_CONTROLLER_TEST', '审批配置控制器测试角色', 'ACTIVE', 0,
                        'workflow config controller test', 0, 0, 0)
                """, WORKFLOW_CONFIG_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (887902, ?, 5064, 0)",
                WORKFLOW_CONFIG_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (887903, ?, 5065, 0)",
                WORKFLOW_CONFIG_ROLE_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("""
                delete from wf_approval_node_approver
                where node_id in (
                    select n.id
                    from wf_approval_node n
                    join wf_approval_config c on c.id = n.config_id
                    where c.business_type = 'SALES_ORDER'
                      and c.created_by = ?
                )
                """, USER_ID);
        jdbcTemplate.update("""
                delete from wf_approval_node
                where config_id in (
                    select id
                    from wf_approval_config
                    where business_type = 'SALES_ORDER'
                      and created_by = ?
                )
                """, USER_ID);
        jdbcTemplate.update("delete from wf_approval_config where business_type = 'SALES_ORDER' and created_by = ?", USER_ID);
        jdbcTemplate.update("delete from sys_refresh_token where user_id = ?", USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username = ?", USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", USER_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", WORKFLOW_CONFIG_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", WORKFLOW_CONFIG_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id = ?", USER_ID);
    }
}

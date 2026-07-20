package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowNotificationIntegrationTest {

    private static final long SUBMITTER_ID = 886001L;
    private static final long APPROVER_ID = 886002L;
    private static final long OTHER_COMPANY_APPROVER_ID = 886003L;
    private static final long FALLBACK_VIEWER_ID = 886004L;
    private static final long SECOND_APPROVER_ID = 886005L;
    private static final long APPROVER_ROLE_ID = 886401L;
    private static final long OTHER_COMPANY_APPROVER_ROLE_ID = 886402L;
    private static final long BUSINESS_ID = 886101L;
    private static final String BUSINESS_NO = "WF-NOTIFY-001";

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedUser(SUBMITTER_ID, "wf_notify_submitter", 1L, 1L);
        seedUser(APPROVER_ID, "wf_notify_approver", 1L, 1L);
        seedUser(OTHER_COMPANY_APPROVER_ID, "wf_notify_other_company", 2L, 1L);
        seedUser(FALLBACK_VIEWER_ID, "wf_notify_fallback_viewer", 1L, 1L);
        seedUser(SECOND_APPROVER_ID, "wf_notify_second_approver", 1L, 1L);
        seedWorkflowViewerRole(APPROVER_ROLE_ID, 1L, "WF_NOTIFY_APPROVER", 886911L);
        seedWorkflowViewerRole(OTHER_COMPANY_APPROVER_ROLE_ID, 2L, "WF_NOTIFY_OTHER_APPROVER", 886912L);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (886901, ?, ?, 0)",
                APPROVER_ID, APPROVER_ROLE_ID);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (886902, ?, ?, 0)",
                OTHER_COMPANY_APPROVER_ID, OTHER_COMPANY_APPROVER_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void workflowSubmitCreatesTodoOnlyForSameCompanyApproverAndApproveNotifiesSubmitter() {
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        Integer approverTodoCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and n.category = 'TODO'
                  and n.notification_type = 'WORKFLOW_APPROVAL_PENDING'
                  and r.recipient_user_id = ?
                  and r.status = 'ACTIVE'
                  and r.read_flag = 0
                """, Integer.class, BUSINESS_NO, APPROVER_ID);
        Assertions.assertThat(approverTodoCount).isEqualTo(1);

        Integer otherCompanyTodoCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and r.recipient_user_id = ?
                """, Integer.class, BUSINESS_NO, OTHER_COMPANY_APPROVER_ID);
        Assertions.assertThat(otherCompanyTodoCount).isZero();

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "approve");

        Integer activeTodoCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and n.category = 'TODO'
                  and r.recipient_user_id = ?
                  and r.status = 'ACTIVE'
                """, Integer.class, BUSINESS_NO, APPROVER_ID);
        Assertions.assertThat(activeTodoCount).isZero();

        Integer submitterResultCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and n.category = 'NOTICE'
                  and n.notification_type = 'WORKFLOW_APPROVED'
                  and r.recipient_user_id = ?
                  and r.read_flag = 0
                  and r.status = 'ACTIVE'
                """, Integer.class, BUSINESS_NO, SUBMITTER_ID);
        Assertions.assertThat(submitterResultCount).isEqualTo(1);
    }

    @Test
    void workflowRejectWithoutCommentNotifiesSubmitterWithRejectDefaultContent() {
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.reject("SALES_ORDER", BUSINESS_ID, " ");

        String rejectContent = jdbcTemplate.queryForObject("""
                select n.content
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and n.category = 'NOTICE'
                  and n.notification_type = 'WORKFLOW_REJECTED'
                  and r.recipient_user_id = ?
                  and r.read_flag = 0
                  and r.status = 'ACTIVE'
                """, String.class, BUSINESS_NO, SUBMITTER_ID);
        Assertions.assertThat(rejectContent).isEqualTo("审批驳回：销售订单审批 " + BUSINESS_NO);
    }

    @Test
    void workflowSubmitUsesConfiguredApproverRoleBeforePermissionFallback() {
        seedWorkflowRoleConfig("SALES_ORDER", APPROVER_ROLE_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        Integer configuredApproverTodoCount = todoCountFor(APPROVER_ID);
        Integer fallbackViewerTodoCount = todoCountFor(FALLBACK_VIEWER_ID);
        Integer otherCompanyTodoCount = todoCountFor(OTHER_COMPANY_APPROVER_ID);

        Assertions.assertThat(configuredApproverTodoCount).isEqualTo(1);
        Assertions.assertThat(fallbackViewerTodoCount).isZero();
        Assertions.assertThat(otherCompanyTodoCount).isZero();
    }

    @Test
    void workflowApproveRejectsUserOutsideConfiguredApprovers() {
        seedWorkflowRoleConfig("SALES_ORDER", APPROVER_ROLE_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        useUser(FALLBACK_VIEWER_ID, 1L, 1L, "wf_notify_fallback_viewer");
        Assertions.assertThatThrownBy(() -> workflowService.approve("SALES_ORDER", BUSINESS_ID, "approve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前用户不是该单据审批人");

        String instanceStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_instance
                where business_no = ?
                """, String.class, BUSINESS_NO);
        String taskStatus = jdbcTemplate.queryForObject("""
                select status
                from wf_approval_task
                where business_no = ?
                """, String.class, BUSINESS_NO);

        Assertions.assertThat(instanceStatus).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(taskStatus).isEqualTo("PENDING");
        Assertions.assertThat(todoCountFor(APPROVER_ID)).isEqualTo(1);
    }

    @Test
    void workflowApproveAdvancesThroughConfiguredNodesBeforeCompleting() {
        seedWorkflowTwoNodeConfig("SALES_ORDER", APPROVER_ROLE_ID, SECOND_APPROVER_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        Assertions.assertThat(todoCountFor(APPROVER_ID)).isEqualTo(1);
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isZero();

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "first approve");

        Assertions.assertThat(instanceStatus()).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(taskCountByStatus("APPROVED")).isEqualTo(1);
        Assertions.assertThat(taskCountByStatus("PENDING")).isEqualTo(1);
        Assertions.assertThat(todoCountFor(APPROVER_ID)).isZero();
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isEqualTo(1);

        useUser(SECOND_APPROVER_ID, 1L, 1L, "wf_notify_second_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "second approve");

        Assertions.assertThat(instanceStatus()).isEqualTo("APPROVED");
        Assertions.assertThat(taskCountByStatus("APPROVED")).isEqualTo(2);
        Assertions.assertThat(taskCountByStatus("PENDING")).isZero();
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isZero();
    }

    @Test
    void workflowAllModeWaitsForEveryConfiguredApproverBeforeCompletingNode() {
        seedWorkflowAllUserConfig("SALES_ORDER", APPROVER_ID, SECOND_APPROVER_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        Assertions.assertThat(todoCountFor(APPROVER_ID)).isEqualTo(1);
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isEqualTo(1);

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "first approve");

        Assertions.assertThat(instanceStatus()).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(taskCountByStatus("PENDING")).isEqualTo(1);
        Assertions.assertThat(taskCountByStatus("APPROVED")).isZero();
        Assertions.assertThat(recordCountByAction("APPROVE")).isEqualTo(1);
        Assertions.assertThat(todoCountFor(APPROVER_ID)).isZero();
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isEqualTo(1);

        useUser(SECOND_APPROVER_ID, 1L, 1L, "wf_notify_second_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "second approve");

        Assertions.assertThat(instanceStatus()).isEqualTo("APPROVED");
        Assertions.assertThat(taskCountByStatus("PENDING")).isZero();
        Assertions.assertThat(taskCountByStatus("APPROVED")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("APPROVE")).isEqualTo(2);
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isZero();
    }

    @Test
    void workflowAllModeRejectsDuplicateApprovalOnSameNode() {
        seedWorkflowAllUserConfig("SALES_ORDER", APPROVER_ID, SECOND_APPROVER_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "first approve");

        Assertions.assertThatThrownBy(() -> workflowService.approve("SALES_ORDER", BUSINESS_ID, "duplicate approve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前用户已审批该节点");

        Assertions.assertThat(instanceStatus()).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(taskCountByStatus("PENDING")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("APPROVE")).isEqualTo(1);
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isEqualTo(1);
    }

    @Test
    void workflowSubmitterCanWithdrawBeforeAnyApproval() {
        seedWorkflowRoleConfig("SALES_ORDER", APPROVER_ROLE_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        Assertions.assertThat(todoCountFor(APPROVER_ID)).isEqualTo(1);

        workflowService.withdraw("SALES_ORDER", BUSINESS_ID, "withdraw");

        Assertions.assertThat(instanceStatus()).isEqualTo("WITHDRAWN");
        Assertions.assertThat(taskCountByStatus("PENDING")).isZero();
        Assertions.assertThat(taskCountByStatus("CANCELLED")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("WITHDRAW")).isEqualTo(1);
        Assertions.assertThat(todoCountFor(APPROVER_ID)).isZero();
    }

    @Test
    void workflowWithdrawRejectsWhenApprovalAlreadyStarted() {
        seedWorkflowAllUserConfig("SALES_ORDER", APPROVER_ID, SECOND_APPROVER_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "submit");

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.approve("SALES_ORDER", BUSINESS_ID, "first approve");

        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");
        Assertions.assertThatThrownBy(() -> workflowService.withdraw("SALES_ORDER", BUSINESS_ID, "withdraw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批已被处理，不能撤回");

        Assertions.assertThat(instanceStatus()).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(taskCountByStatus("PENDING")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("APPROVE")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("WITHDRAW")).isZero();
        Assertions.assertThat(todoCountFor(SECOND_APPROVER_ID)).isEqualTo(1);
    }

    @Test
    void workflowRejectedInstanceCanBeSubmittedRejectedAndSubmittedAgain() {
        seedWorkflowRoleConfig("SALES_ORDER", APPROVER_ROLE_ID);
        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");

        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "first submit");

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.reject("SALES_ORDER", BUSINESS_ID, "first reject");

        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");
        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "second submit");

        useUser(APPROVER_ID, 1L, 1L, "wf_notify_approver");
        workflowService.reject("SALES_ORDER", BUSINESS_ID, "second reject");

        useUser(SUBMITTER_ID, 1L, 1L, "wf_notify_submitter");
        workflowService.submit("SALES_ORDER", BUSINESS_ID, BUSINESS_NO, "销售订单审批 " + BUSINESS_NO, "third submit");

        Assertions.assertThat(instanceStatus()).isEqualTo("IN_APPROVAL");
        Assertions.assertThat(instanceCount()).isEqualTo(3);
        Assertions.assertThat(instanceCountByStatus("REJECTED")).isEqualTo(2);
        Assertions.assertThat(instanceCountByStatus("IN_APPROVAL")).isEqualTo(1);
        Assertions.assertThat(recordCountByAction("SUBMIT")).isEqualTo(3);
        Assertions.assertThat(recordCountByAction("REJECT")).isEqualTo(2);
        Assertions.assertThat(todoCountFor(APPROVER_ID)).isEqualTo(1);
    }

    private Integer todoCountFor(long recipientUserId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from sys_notification n
                join sys_notification_recipient r on r.notification_id = n.id
                where n.business_no = ?
                  and n.category = 'TODO'
                  and n.notification_type = 'WORKFLOW_APPROVAL_PENDING'
                  and r.recipient_user_id = ?
                  and r.status = 'ACTIVE'
                  and r.read_flag = 0
                """, Integer.class, BUSINESS_NO, recipientUserId);
    }

    private String instanceStatus() {
        return jdbcTemplate.queryForObject("""
                select status
                from wf_approval_instance
                where business_no = ?
                order by submit_time desc, id desc
                limit 1
                """, String.class, BUSINESS_NO);
    }

    private Integer instanceCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_instance
                where business_no = ?
                """, Integer.class, BUSINESS_NO);
    }

    private Integer instanceCountByStatus(String status) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_instance
                where business_no = ?
                  and status = ?
                """, Integer.class, BUSINESS_NO, status);
    }

    private Integer taskCountByStatus(String status) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_task
                where business_no = ?
                  and status = ?
                """, Integer.class, BUSINESS_NO, status);
    }

    private Integer recordCountByAction(String action) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from wf_approval_record
                where business_no = ?
                  and action = ?
                """, Integer.class, BUSINESS_NO, action);
    }

    private void seedWorkflowRoleConfig(String businessType, long roleId) {
        jdbcTemplate.update("""
                insert into wf_approval_config
                (id, company_id, account_book_id, business_type, config_name, status, deleted_flag, created_by, updated_by, version)
                values (886701, 1, 1, ?, ?, 'ACTIVE', 0, 0, 0, 0)
                """, businessType, "测试审批配置");
        jdbcTemplate.update("""
                insert into wf_approval_node
                (id, company_id, config_id, node_name, node_order, approval_mode, status, created_by, updated_by, version)
                values (886711, 1, 886701, '一级审批', 1, 'ANY', 'ACTIVE', 0, 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wf_approval_node_approver
                (id, company_id, node_id, approver_type, approver_id, created_by, updated_by, version)
                values (886721, 1, 886711, 'ROLE', ?, 0, 0, 0)
                """, roleId);
    }

    private void seedWorkflowTwoNodeConfig(String businessType, long firstRoleId, long secondUserId) {
        seedWorkflowRoleConfig(businessType, firstRoleId);
        jdbcTemplate.update("""
                insert into wf_approval_node
                (id, company_id, config_id, node_name, node_order, approval_mode, status, created_by, updated_by, version)
                values (886712, 1, 886701, '二级审批', 2, 'ANY', 'ACTIVE', 0, 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wf_approval_node_approver
                (id, company_id, node_id, approver_type, approver_id, created_by, updated_by, version)
                values (886722, 1, 886712, 'USER', ?, 0, 0, 0)
                """, secondUserId);
    }

    private void seedWorkflowAllUserConfig(String businessType, long firstUserId, long secondUserId) {
        jdbcTemplate.update("""
                insert into wf_approval_config
                (id, company_id, account_book_id, business_type, config_name, status, deleted_flag, created_by, updated_by, version)
                values (886701, 1, 1, ?, ?, 'ACTIVE', 0, 0, 0, 0)
                """, businessType, "测试会签审批配置");
        jdbcTemplate.update("""
                insert into wf_approval_node
                (id, company_id, config_id, node_name, node_order, approval_mode, status, created_by, updated_by, version)
                values (886711, 1, 886701, '会签审批', 1, 'ALL', 'ACTIVE', 0, 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wf_approval_node_approver
                (id, company_id, node_id, approver_type, approver_id, created_by, updated_by, version)
                values (886721, 1, 886711, 'USER', ?, 0, 0, 0)
                """, firstUserId);
        jdbcTemplate.update("""
                insert into wf_approval_node_approver
                (id, company_id, node_id, approver_type, approver_id, created_by, updated_by, version)
                values (886722, 1, 886711, 'USER', ?, 0, 0, 0)
                """, secondUserId);
    }

    private void seedUser(long id, String username, long companyId, long accountBookId) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, ?, ?, ?, 'N/A', ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'workflow notification test', 0, 0, 0)
                """, id, companyId, accountBookId, username, "EMP_" + id, username);
    }

    private void seedWorkflowViewerRole(long roleId, long companyId, String roleCode, long roleMenuId) {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, ?, 1, ?, '审批通知测试角色', 'ACTIVE', 0, 'workflow notification test', 0, 0, 0)
                """, roleId, companyId, roleCode);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (?, ?, 5011, 0)",
                roleMenuId, roleId);
    }

    private void useUser(long userId, long companyId, long accountBookId, String username) {
        TestSecurityContexts.useUser(
                userId,
                companyId,
                accountBookId,
                1L,
                1L,
                username,
                username,
                PermissionCodes.allPermissions(),
                DataScopeSnapshot.all()
        );
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_notification_recipient where recipient_user_id in (?, ?, ?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, OTHER_COMPANY_APPROVER_ID, FALLBACK_VIEWER_ID, SECOND_APPROVER_ID);
        jdbcTemplate.update("delete from sys_notification where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from wf_approval_task where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from wf_approval_record where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from wf_approval_instance where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from wf_approval_node_approver where id between 886700 and 886799");
        jdbcTemplate.update("delete from wf_approval_node where id between 886700 and 886799");
        jdbcTemplate.update("delete from wf_approval_config where id between 886700 and 886799");
        jdbcTemplate.update("delete from sys_audit_log where business_no = ?", BUSINESS_NO);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?, ?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, OTHER_COMPANY_APPROVER_ID, FALLBACK_VIEWER_ID, SECOND_APPROVER_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id in (?, ?)", APPROVER_ROLE_ID, OTHER_COMPANY_APPROVER_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id in (?, ?)", APPROVER_ROLE_ID, OTHER_COMPANY_APPROVER_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?, ?, ?, ?)",
                SUBMITTER_ID, APPROVER_ID, OTHER_COMPANY_APPROVER_ID, FALLBACK_VIEWER_ID, SECOND_APPROVER_ID);
    }
}

package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
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
class WorkflowServiceIsolationTest {

    private static final long BIZ_ID_BASE = 981000L;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void listTasksOnlyReturnsCurrentCompanyAndAccountBookTasks() {
        submitWorkflow(2101L, 1L, 1L, BIZ_ID_BASE + 1, "WF-ISO-TASK-C1-B1");
        submitWorkflow(2102L, 2L, 1L, BIZ_ID_BASE + 2, "WF-ISO-TASK-C2-B1");
        submitWorkflow(2103L, 1L, 2L, BIZ_ID_BASE + 3, "WF-ISO-TASK-C1-B2");

        useUser(2991L, 1L, 1L, "wf-task-viewer");
        PageResponse<WorkflowTaskResponse> response = workflowService.listTasks(new WorkflowTaskPageQuery());

        Assertions.assertThat(response.total()).isEqualTo(1L);
        Assertions.assertThat(response.records())
                .extracting(WorkflowTaskResponse::businessNo)
                .containsExactly("WF-ISO-TASK-C1-B1");
    }

    @Test
    void listTasksFiltersByBusinessId() {
        submitWorkflow(2101L, 1L, 1L, BIZ_ID_BASE + 101, "WF-ISO-TASK-BIZ-101");
        submitWorkflow(2102L, 1L, 1L, BIZ_ID_BASE + 102, "WF-ISO-TASK-BIZ-102");

        useUser(2991L, 1L, 1L, "wf-task-viewer");
        WorkflowTaskPageQuery query = new WorkflowTaskPageQuery();
        query.setBusinessId(BIZ_ID_BASE + 102);
        PageResponse<WorkflowTaskResponse> response = workflowService.listTasks(query);

        Assertions.assertThat(response.total()).isEqualTo(1L);
        Assertions.assertThat(response.records())
                .extracting(WorkflowTaskResponse::businessNo)
                .containsExactly("WF-ISO-TASK-BIZ-102");
    }

    @Test
    void listRecordsOnlyReturnsCurrentCompanyAndAccountBookRecords() {
        submitWorkflow(2201L, 1L, 1L, BIZ_ID_BASE + 11, "WF-ISO-RECORD-C1-B1");
        submitWorkflow(2202L, 2L, 1L, BIZ_ID_BASE + 12, "WF-ISO-RECORD-C2-B1");
        submitWorkflow(2203L, 1L, 2L, BIZ_ID_BASE + 13, "WF-ISO-RECORD-C1-B2");

        useUser(2992L, 1L, 1L, "wf-record-viewer");
        PageResponse<WorkflowRecordResponse> response = workflowService.listRecords(new WorkflowRecordPageQuery());

        Assertions.assertThat(response.total()).isEqualTo(1L);
        Assertions.assertThat(response.records())
                .extracting(WorkflowRecordResponse::businessNo)
                .containsExactly("WF-ISO-RECORD-C1-B1");
    }

    @Test
    void listRecordsFiltersByBusinessId() {
        submitWorkflow(2201L, 1L, 1L, BIZ_ID_BASE + 111, "WF-ISO-RECORD-BIZ-111");
        submitWorkflow(2202L, 1L, 1L, BIZ_ID_BASE + 112, "WF-ISO-RECORD-BIZ-112");

        useUser(2992L, 1L, 1L, "wf-record-viewer");
        WorkflowRecordPageQuery query = new WorkflowRecordPageQuery();
        query.setBusinessId(BIZ_ID_BASE + 112);
        PageResponse<WorkflowRecordResponse> response = workflowService.listRecords(query);

        Assertions.assertThat(response.total()).isEqualTo(1L);
        Assertions.assertThat(response.records())
                .extracting(WorkflowRecordResponse::businessNo)
                .containsExactly("WF-ISO-RECORD-BIZ-112");
    }

    @Test
    void approvalInfoReturnsNotSubmittedWhenInstanceBelongsToDifferentAccountBook() {
        submitWorkflow(2301L, 1L, 2L, BIZ_ID_BASE + 21, "WF-ISO-INFO-C1-B2");

        useUser(2993L, 1L, 1L, "wf-info-viewer");
        WorkflowApprovalInfoResponse response = workflowService.approvalInfo("SALES_ORDER", BIZ_ID_BASE + 21);

        Assertions.assertThat(response.instanceId()).isNull();
        Assertions.assertThat(response.status()).isEqualTo("NOT_SUBMITTED");
        Assertions.assertThat(response.records()).isEmpty();
    }

    @Test
    void approveCannotOperateOnDifferentAccountBookInstance() {
        submitWorkflow(2401L, 1L, 2L, BIZ_ID_BASE + 31, "WF-ISO-APPROVE-C1-B2");

        useUser(2994L, 1L, 1L, "wf-approver");
        Assertions.assertThatThrownBy(() -> workflowService.approve("SALES_ORDER", BIZ_ID_BASE + 31, "approve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批实例不存在或已完成");
    }

    private void submitWorkflow(long userId, long companyId, long accountBookId, long businessId, String businessNo) {
        useUser(userId, companyId, accountBookId, businessNo.toLowerCase());
        workflowService.submit("SALES_ORDER", businessId, businessNo, "workflow isolation test " + businessNo, "submit");
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
        jdbcTemplate.update("delete from wf_approval_task");
        jdbcTemplate.update("delete from wf_approval_record");
        jdbcTemplate.update("delete from wf_approval_instance");
        jdbcTemplate.update("delete from sys_audit_log where business_no like 'WF-ISO-%'");
    }
}

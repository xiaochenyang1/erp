package com.tuowei.erp.workflow.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowRecordEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRecordCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            71L,
            81L,
            91L,
            LocalDateTime.parse("2026-08-14T10:15:00")
    );

    @Mock
    private WorkflowRecordMapper recordMapper;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private CurrentUserContext currentUserContext;
    @InjectMocks
    private WorkflowRecordCommandService service;

    @Test
    void writesWorkflowHistoryAndMatchingSystemAudit() {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(101L);
        instance.setBusinessType("SALES_ORDER");
        instance.setBusinessId(201L);
        instance.setBusinessNo("SO-201");
        when(currentUserContext.requireCurrentUser()).thenReturn(
                new CurrentUser(71L, 81L, 91L, null, null, "approver", "Approver")
        );

        service.record(instance, "TRANSFER", 301L, "transition note", AUDIT, AUDIT.now());

        ArgumentCaptor<WorkflowRecordEntity> recordCaptor = ArgumentCaptor.forClass(WorkflowRecordEntity.class);
        verify(recordMapper).insert(recordCaptor.capture());
        WorkflowRecordEntity record = recordCaptor.getValue();
        assertThat(record.getCompanyId()).isEqualTo(81L);
        assertThat(record.getAccountBookId()).isEqualTo(91L);
        assertThat(record.getInstanceId()).isEqualTo(101L);
        assertThat(record.getBusinessType()).isEqualTo("SALES_ORDER");
        assertThat(record.getBusinessId()).isEqualTo(201L);
        assertThat(record.getBusinessNo()).isEqualTo("SO-201");
        assertThat(record.getAction()).isEqualTo("TRANSFER");
        assertThat(record.getApprovalNodeId()).isEqualTo(301L);
        assertThat(record.getOperatorUserId()).isEqualTo(71L);
        assertThat(record.getComment()).isEqualTo("transition note");
        assertThat(record.getActionTime()).isEqualTo(AUDIT.now());
        assertThat(record.getCreatedBy()).isEqualTo(71L);
        assertThat(record.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(record.getUpdatedBy()).isEqualTo(71L);
        assertThat(record.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(record.getVersion()).isZero();
        verify(systemLogService).recordAudit(
                "WORKFLOW",
                "SALES_ORDER",
                201L,
                "SO-201",
                "TRANSFER",
                71L,
                "approver",
                null,
                "transition note",
                AUDIT.now()
        );
    }
}

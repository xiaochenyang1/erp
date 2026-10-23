package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.service.WorkflowCommandService;
import com.tuowei.erp.workflow.service.WorkflowQueryService;
import com.tuowei.erp.workflow.service.WorkflowRecordCommandService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.service.WorkflowTaskTransitionService;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Write-side unit tests for {@link WorkflowService}. The read-side behaviour (default pagination,
 * scoped instance filtering, overdue-only filtering) is now covered by {@link WorkflowQueryServiceTest}
 * after the E-1 query/posting split; this class keeps the submit-timeout assertion that belongs to the
 * posting/orchestration side.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class WorkflowServiceQueryDefaultsTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @Test
    void submitUsesBusinessWorkflowTimeoutForNewTaskDeadline() {
        WorkflowInstanceMapper instanceMapper = mock(WorkflowInstanceMapper.class);
        WorkflowTaskMapper taskMapper = mock(WorkflowTaskMapper.class);
        WorkflowApprovalConfigService configService = mock(WorkflowApprovalConfigService.class);
        when(configService.resolveTaskTimeoutHours(any(), any(), anyLong())).thenReturn(6L);
        when(configService.resolvePendingApproverUserIds(any(), any(), any())).thenReturn(List.of(77L));
        // Wire a real WorkflowQueryService with the mocked mappers so the facade's write path
        // (closeActiveInstanceIfPresent -> findActiveInstance) gets a real scoped query wrapper;
        // the mocked instanceMapper then returns null and the no-op early-return is taken.
        WorkflowQueryService queryService = new WorkflowQueryService(
                instanceMapper, taskMapper, mock(WorkflowRecordMapper.class), auditFactory());
        WorkflowCommandService commandService = new WorkflowCommandService(
                instanceMapper, taskMapper, mock(WorkflowRecordMapper.class), auditFactory(),
                mock(NotificationService.class), configService, queryService,
                mock(WorkflowRecordCommandService.class));
        WorkflowService service = new WorkflowService(
                queryService, commandService, mock(WorkflowTaskTransitionService.class));

        service.submit("SALES_ORDER", 7001L, "SO-7001", "销售订单 SO-7001", null);

        ArgumentCaptor<WorkflowTaskEntity> taskCaptor = ArgumentCaptor.forClass(WorkflowTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getDueTime()).isEqualTo(AUDIT.now().plusHours(6));
        assertThat(taskCaptor.getValue().getApproverUserId()).isEqualTo(77L);
        verify(configService).resolveTaskTimeoutHours(any(WorkflowInstanceEntity.class), any(AuditMetadata.class), anyLong());
    }

    @Test
    void approveHonorsExplicitTaskAssigneeAfterTransfer() {
        WorkflowInstanceMapper instanceMapper = mock(WorkflowInstanceMapper.class);
        WorkflowTaskMapper taskMapper = mock(WorkflowTaskMapper.class);
        WorkflowRecordMapper recordMapper = mock(WorkflowRecordMapper.class);
        WorkflowApprovalConfigService configService = mock(WorkflowApprovalConfigService.class);
        NotificationService notificationService = mock(NotificationService.class);
        WorkflowRecordCommandService recordCommandService = mock(WorkflowRecordCommandService.class);
        AuditMetadataFactory auditMetadataFactory = auditFactory();
        WorkflowQueryService queryService = new WorkflowQueryService(
                instanceMapper,
                taskMapper,
                recordMapper,
                auditMetadataFactory
        );
        WorkflowCommandService commandService = new WorkflowCommandService(
                instanceMapper,
                taskMapper,
                recordMapper,
                auditMetadataFactory,
                notificationService,
                configService,
                queryService,
                recordCommandService
        );
        WorkflowService service = new WorkflowService(
                queryService,
                commandService,
                mock(WorkflowTaskTransitionService.class)
        );
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(8001L);
        instance.setBusinessType("SALES_ORDER");
        instance.setBusinessId(7001L);
        instance.setBusinessNo("SO-7001");
        instance.setSubmitUserId(8L);
        instance.setStatus("IN_APPROVAL");
        WorkflowTaskEntity task = new WorkflowTaskEntity();
        task.setId(9001L);
        task.setInstanceId(instance.getId());
        task.setApprovalNodeId(6001L);
        task.setApproverUserId(AUDIT.userId());
        task.setStatus("PENDING");
        when(instanceMapper.selectOne(any())).thenReturn(instance);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);
        when(instanceMapper.updateById(instance)).thenReturn(1);

        assertThat(service.approve("SALES_ORDER", 7001L, "approved by transferee")).isTrue();

        assertThat(task.getStatus()).isEqualTo("APPROVED");
        assertThat(instance.getStatus()).isEqualTo("APPROVED");
        verify(configService, never()).assertCurrentUserCanApprove(any(), any(), any());
        verify(configService, never()).isAllApprovalMode(any(), any(), any());
        verify(recordCommandService).record(
                instance,
                "APPROVE",
                6001L,
                "approved by transferee",
                AUDIT,
                AUDIT.now()
        );
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }
}

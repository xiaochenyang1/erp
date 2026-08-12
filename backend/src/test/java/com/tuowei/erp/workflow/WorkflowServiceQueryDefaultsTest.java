package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.service.WorkflowQueryService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(new CurrentUser(9L, 101L, 202L, null, null, "tester", "Tester"));
        when(configService.resolveTaskTimeoutHours(any(), any(), anyLong())).thenReturn(6L);
        // Wire a real WorkflowQueryService with the mocked mappers so the facade's write path
        // (closeActiveInstanceIfPresent -> findActiveInstance) gets a real scoped query wrapper;
        // the mocked instanceMapper then returns null and the no-op early-return is taken.
        WorkflowQueryService queryService = new WorkflowQueryService(
                instanceMapper, taskMapper, mock(WorkflowRecordMapper.class), auditFactory());
        WorkflowService service = new WorkflowService(
                instanceMapper, taskMapper, mock(WorkflowRecordMapper.class), auditFactory(),
                mock(SystemLogService.class), currentUserContext, mock(NotificationService.class),
                configService, mock(com.tuowei.erp.system.user.mapper.UserMapper.class),
                queryService);

        service.submit("SALES_ORDER", 7001L, "SO-7001", "销售订单 SO-7001", null);

        ArgumentCaptor<WorkflowTaskEntity> taskCaptor = ArgumentCaptor.forClass(WorkflowTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getDueTime()).isEqualTo(AUDIT.now().plusHours(6));
        verify(configService).resolveTaskTimeoutHours(any(WorkflowInstanceEntity.class), any(AuditMetadata.class), anyLong());
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }
}

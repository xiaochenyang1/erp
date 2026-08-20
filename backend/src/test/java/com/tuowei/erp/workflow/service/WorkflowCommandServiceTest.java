package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            701L,
            801L,
            901L,
            LocalDateTime.parse("2026-08-20T10:15:00")
    );

    @Mock
    private WorkflowInstanceMapper instanceMapper;
    @Mock
    private WorkflowTaskMapper taskMapper;
    @Mock
    private WorkflowRecordMapper recordMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WorkflowApprovalConfigService approvalConfigService;
    @Mock
    private WorkflowQueryService queryService;
    @Mock
    private WorkflowRecordCommandService recordCommandService;

    private WorkflowCommandService service;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
        lenient().when(queryService.buildScopedInstanceQuery(any(AuditMetadata.class)))
                .thenReturn(new LambdaQueryWrapper<>());
        service = new WorkflowCommandService(
                instanceMapper,
                taskMapper,
                recordMapper,
                auditMetadataFactory,
                notificationService,
                approvalConfigService,
                queryService,
                recordCommandService
        );
        ReflectionTestUtils.setField(service, "taskTimeoutHours", 24L);
    }

    @Test
    void cancelWithoutActiveInstanceIsNoOp() {
        when(instanceMapper.selectOne(any())).thenReturn(null);

        service.cancel("SALES_ORDER", 1001L, "cancel");

        verify(instanceMapper).selectOne(any());
        verify(instanceMapper, never()).updateById(any(WorkflowInstanceEntity.class));
        verifyNoInteractions(taskMapper, notificationService, recordCommandService);
    }

    @Test
    void cancelClosesCurrentTaskAndWritesAuditRecord() {
        WorkflowInstanceEntity instance = activeInstance(3001L, 1001L, AUDIT.userId());
        WorkflowTaskEntity task = pendingTask(4001L, instance.getId());
        when(instanceMapper.selectOne(any())).thenReturn(instance);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(instanceMapper.updateById(instance)).thenReturn(1);
        when(taskMapper.updateById(task)).thenReturn(1);

        service.cancel("SALES_ORDER", 1001L, "cancel");

        assertThat(instance.getStatus()).isEqualTo("CANCELLED");
        assertThat(task.getStatus()).isEqualTo("CANCELLED");
        verify(notificationService).closeWorkflowPending(instance, AUDIT, AUDIT.now());
        verify(recordCommandService).record(instance, "CANCEL", "cancel", AUDIT, AUDIT.now());
    }

    @Test
    void cancelStopsBeforeTaskAndNotificationWhenInstanceUpdateLosesRace() {
        WorkflowInstanceEntity instance = activeInstance(3002L, 1002L, AUDIT.userId());
        when(instanceMapper.selectOne(any())).thenReturn(instance);
        when(instanceMapper.updateById(instance)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel("SALES_ORDER", 1002L, "cancel"))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("审批实例已被其他操作修改，请刷新后重试");

        verify(taskMapper, never()).selectOne(any());
        verifyNoInteractions(notificationService, recordCommandService);
    }

    @Test
    void taskApprovalRejectsBusinessMismatchBeforeLoadingInstance() {
        WorkflowTaskEntity task = pendingTask(4002L, 3003L);
        task.setBusinessType("PURCHASE_ORDER");
        task.setBusinessId(2002L);
        when(queryService.requireScopedTask(task.getId())).thenReturn(task);

        assertThatThrownBy(() -> service.approveTaskForBusiness(
                task.getId(), "SALES_ORDER", 1003L, "approve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批任务与业务单据不匹配");

        verify(queryService).requireScopedTask(task.getId());
        verifyNoInteractions(instanceMapper, taskMapper, recordMapper, notificationService, recordCommandService);
    }

    @Test
    void withdrawRejectsNonSubmitterWithoutChangingWorkflow() {
        WorkflowInstanceEntity instance = activeInstance(3004L, 1004L, 999L);
        when(instanceMapper.selectOne(any())).thenReturn(instance);

        assertThatThrownBy(() -> service.withdraw("SALES_ORDER", 1004L, "withdraw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有提交人可以撤回审批");

        verify(instanceMapper, never()).updateById(any(WorkflowInstanceEntity.class));
        verifyNoInteractions(taskMapper, notificationService, recordCommandService);
    }

    private WorkflowInstanceEntity activeInstance(Long id, Long businessId, Long submitUserId) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(id);
        instance.setCompanyId(AUDIT.companyId());
        instance.setAccountBookId(AUDIT.accountBookId());
        instance.setBusinessType("SALES_ORDER");
        instance.setBusinessId(businessId);
        instance.setStatus("IN_APPROVAL");
        instance.setSubmitUserId(submitUserId);
        return instance;
    }

    private WorkflowTaskEntity pendingTask(Long id, Long instanceId) {
        WorkflowTaskEntity task = new WorkflowTaskEntity();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setBusinessType("SALES_ORDER");
        task.setBusinessId(1001L);
        task.setStatus("PENDING");
        return task;
    }
}

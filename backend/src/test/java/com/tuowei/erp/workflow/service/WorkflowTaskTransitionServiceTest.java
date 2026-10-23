package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class WorkflowTaskTransitionServiceTest {

    private static final long TASK_ID = 4101L;
    private static final long INSTANCE_ID = 5101L;
    private static final long NODE_ID = 6101L;
    private static final long CURRENT_USER_ID = 7101L;
    private static final long TARGET_USER_ID = 8101L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            CURRENT_USER_ID,
            1101L,
            1201L,
            LocalDateTime.parse("2026-08-14T09:30:00")
    );

    @Mock
    private WorkflowInstanceMapper instanceMapper;
    @Mock
    private WorkflowTaskMapper taskMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WorkflowApprovalConfigService approvalConfigService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WorkflowQueryService queryService;
    @Mock
    private WorkflowRecordCommandService recordCommandService;

    private WorkflowTaskTransitionService service;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(UserEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                UserEntity.class.getName()
        );
        assistant.setCurrentNamespace(UserEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new WorkflowTaskTransitionService(
                instanceMapper,
                taskMapper,
                auditMetadataFactory,
                notificationService,
                approvalConfigService,
                userMapper,
                queryService,
                recordCommandService
        );
        ReflectionTestUtils.setField(service, "taskTimeoutHours", 24L);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void transferReassignsOwnTaskAndKeepsNotificationBestEffort() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now().plusHours(1));
        WorkflowTaskEntity refreshed = pendingTask(TARGET_USER_ID, AUDIT.now().plusHours(1));
        WorkflowInstanceEntity instance = instance();
        WorkflowTaskResponse expected = response(TARGET_USER_ID, refreshed.getDueTime(), 0);
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task, refreshed);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserEntity());
        when(taskMapper.updateById(task)).thenReturn(1);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(queryService.toTaskResponse(refreshed, AUDIT.now())).thenReturn(expected);
        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationService)
                .createWorkflowPending(instance, List.of(TARGET_USER_ID), AUDIT, AUDIT.now());

        WorkflowTaskResponse actual = service.transfer(TASK_ID, TARGET_USER_ID, "  hand over  ");

        assertThat(actual).isSameAs(expected);
        assertThat(task.getApproverUserId()).isEqualTo(TARGET_USER_ID);
        assertThat(task.getUpdatedBy()).isEqualTo(CURRENT_USER_ID);
        assertThat(task.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(recordCommandService).record(
                instance,
                "TRANSFER",
                NODE_ID,
                "转签: 7101 -> 8101；hand over",
                AUDIT,
                AUDIT.now()
        );
        verify(notificationService).closeWorkflowPending(instance, AUDIT, AUDIT.now());
        verify(notificationService).createWorkflowPending(
                instance,
                List.of(TARGET_USER_ID),
                AUDIT,
                AUDIT.now()
        );
    }

    @Test
    void transferAllowsConfiguredApproverToClaimAndReassignUnassignedTask() {
        WorkflowTaskEntity task = pendingTask(null, AUDIT.now().plusHours(1));
        WorkflowTaskEntity refreshed = pendingTask(TARGET_USER_ID, AUDIT.now().plusHours(1));
        WorkflowInstanceEntity instance = instance();
        WorkflowTaskResponse expected = response(TARGET_USER_ID, refreshed.getDueTime(), 0);
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task, refreshed);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserEntity());
        when(taskMapper.updateById(task)).thenReturn(1);
        when(queryService.toTaskResponse(refreshed, AUDIT.now())).thenReturn(expected);

        WorkflowTaskResponse actual = service.transfer(TASK_ID, TARGET_USER_ID, null);

        assertThat(actual).isSameAs(expected);
        verify(approvalConfigService).assertCurrentUserCanApprove(instance, NODE_ID, AUDIT);
        verify(recordCommandService).record(
                instance,
                "TRANSFER",
                NODE_ID,
                "转签: 7101 -> 8101",
                AUDIT,
                AUDIT.now()
        );
    }

    @Test
    void transferRejectsTargetOutsideCurrentTenantOrInactive() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now().plusHours(1));
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());

        assertThatThrownBy(() -> service.transfer(TASK_ID, TARGET_USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("转签目标用户不存在或已停用");

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectOne(queryCaptor.capture());
        LambdaQueryWrapper<UserEntity> query = queryCaptor.getValue();
        assertThat(query.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("id", "company_id", "account_book_id", "status", "deleted_flag");
        assertThat(query.getParamNameValuePairs().values())
                .contains(TARGET_USER_ID, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE", 0);
        verify(taskMapper, never()).updateById(any(WorkflowTaskEntity.class));
        verifyNoInteractions(recordCommandService, notificationService);
    }

    @Test
    void transferStopsBeforeAuditWhenOptimisticUpdateLosesRace() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now().plusHours(1));
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance());
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserEntity());
        when(taskMapper.updateById(task)).thenReturn(0);

        assertThatThrownBy(() -> service.transfer(TASK_ID, TARGET_USER_ID, null))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("审批任务已被其他操作修改，请刷新后重试");

        verifyNoInteractions(recordCommandService, notificationService);
    }

    @Test
    void escalateReassignsOverdueTaskAndRecalculatesDeadline() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now().minusMinutes(1));
        task.setEscalationCount(1);
        WorkflowTaskEntity refreshed = pendingTask(TARGET_USER_ID, AUDIT.now().plusHours(6));
        refreshed.setEscalationCount(2);
        WorkflowInstanceEntity instance = instance();
        WorkflowTaskResponse expected = response(TARGET_USER_ID, refreshed.getDueTime(), 2);
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task, refreshed);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserEntity());
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(approvalConfigService.resolveTaskTimeoutHours(instance, AUDIT, 24L)).thenReturn(6L);
        when(taskMapper.updateById(task)).thenReturn(1);
        when(queryService.toTaskResponse(refreshed, AUDIT.now())).thenReturn(expected);

        WorkflowTaskResponse actual = service.escalate(TASK_ID, TARGET_USER_ID, " manager ");

        assertThat(actual).isSameAs(expected);
        assertThat(task.getApproverUserId()).isEqualTo(TARGET_USER_ID);
        assertThat(task.getEscalatedTime()).isEqualTo(AUDIT.now());
        assertThat(task.getEscalationCount()).isEqualTo(2);
        assertThat(task.getDueTime()).isEqualTo(AUDIT.now().plusHours(6));
        verify(recordCommandService).record(
                instance,
                "ESCALATE",
                NODE_ID,
                "超时升级: 7101 -> 8101；manager",
                AUDIT,
                AUDIT.now()
        );
        verify(notificationService).createWorkflowPending(
                instance,
                List.of(TARGET_USER_ID),
                AUDIT,
                AUDIT.now()
        );
        verify(notificationService).closeWorkflowPending(instance, AUDIT, AUDIT.now());
    }

    @Test
    void escalateRejectsTaskAtOrBeforeDeadlineBoundary() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now());
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.escalate(TASK_ID, TARGET_USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批任务尚未超时");

        verifyNoInteractions(userMapper, instanceMapper, recordCommandService, notificationService);
        verify(taskMapper, never()).updateById(any(WorkflowTaskEntity.class));
    }

    @Test
    void escalateKeepsNotificationFailureTransactional() {
        WorkflowTaskEntity task = pendingTask(CURRENT_USER_ID, AUDIT.now().minusMinutes(1));
        WorkflowInstanceEntity instance = instance();
        when(queryService.requireScopedTask(TASK_ID)).thenReturn(task);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserEntity());
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(approvalConfigService.resolveTaskTimeoutHours(instance, AUDIT, 24L)).thenReturn(6L);
        when(taskMapper.updateById(task)).thenReturn(1);
        doThrow(new IllegalStateException("notification unavailable"))
                .when(notificationService)
                .createWorkflowPending(instance, List.of(TARGET_USER_ID), AUDIT, AUDIT.now());

        assertThatThrownBy(() -> service.escalate(TASK_ID, TARGET_USER_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification unavailable");

        verify(recordCommandService).record(
                eq(instance),
                eq("ESCALATE"),
                eq(NODE_ID),
                eq("超时升级: 7101 -> 8101"),
                eq(AUDIT),
                eq(AUDIT.now())
        );
        verify(notificationService).closeWorkflowPending(instance, AUDIT, AUDIT.now());
    }

    private WorkflowTaskEntity pendingTask(Long approverUserId, LocalDateTime dueTime) {
        WorkflowTaskEntity task = new WorkflowTaskEntity();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setApprovalNodeId(NODE_ID);
        task.setApproverUserId(approverUserId);
        task.setStatus("PENDING");
        task.setDueTime(dueTime);
        task.setEscalationCount(0);
        return task;
    }

    private WorkflowInstanceEntity instance() {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(INSTANCE_ID);
        instance.setBusinessType("SALES_ORDER");
        instance.setBusinessId(9101L);
        instance.setBusinessNo("SO-WF-9101");
        instance.setSubmitUserId(9102L);
        return instance;
    }

    private WorkflowTaskResponse response(Long approverUserId, LocalDateTime dueTime, int escalationCount) {
        return new WorkflowTaskResponse(
                TASK_ID,
                INSTANCE_ID,
                "SALES_ORDER",
                9101L,
                "SO-WF-9101",
                "Sales order approval",
                approverUserId,
                "PENDING",
                dueTime,
                false,
                escalationCount == 0 ? null : AUDIT.now(),
                escalationCount,
                AUDIT.now().minusDays(1),
                AUDIT.now()
        );
    }
}

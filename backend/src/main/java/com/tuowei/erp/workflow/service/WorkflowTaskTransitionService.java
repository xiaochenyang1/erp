package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Task reassignment and timeout escalation for pending workflow tasks. */
@Service
public class WorkflowTaskTransitionService {

    private static final String TASK_PENDING = "PENDING";

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final NotificationService notificationService;
    private final WorkflowApprovalConfigService approvalConfigService;
    private final UserMapper userMapper;
    private final WorkflowQueryService queryService;
    private final WorkflowRecordCommandService recordCommandService;

    @Value("${erp.workflow.task-timeout-hours:24}")
    private long taskTimeoutHours;

    public WorkflowTaskTransitionService(
            WorkflowInstanceMapper instanceMapper,
            WorkflowTaskMapper taskMapper,
            AuditMetadataFactory auditMetadataFactory,
            NotificationService notificationService,
            WorkflowApprovalConfigService approvalConfigService,
            UserMapper userMapper,
            WorkflowQueryService queryService,
            WorkflowRecordCommandService recordCommandService
    ) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.notificationService = notificationService;
        this.approvalConfigService = approvalConfigService;
        this.userMapper = userMapper;
        this.queryService = queryService;
        this.recordCommandService = recordCommandService;
    }

    @Transactional
    public WorkflowTaskResponse transfer(Long taskId, Long targetUserId, String comment) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowTaskEntity task = requireCurrentPendingTask(taskId);
        WorkflowInstanceEntity instance = requireInstance(task);
        assertCurrentUserCanTransfer(instance, task, audit);
        if (Objects.equals(targetUserId, audit.userId())) {
            throw new IllegalArgumentException("不能转签给自己");
        }
        if (Objects.equals(targetUserId, instance.getSubmitUserId())) {
            throw new IllegalArgumentException("不能转签给提交人");
        }
        requireActiveTargetUser(targetUserId, audit, "转签目标用户不存在或已停用");

        LocalDateTime now = audit.now();
        Long fromUserId = task.getApproverUserId() == null ? audit.userId() : task.getApproverUserId();
        task.setApproverUserId(targetUserId);
        task.setUpdatedBy(audit.userId());
        task.setUpdatedTime(now);
        updateTask(task);

        String message = transitionMessage("转签", fromUserId, targetUserId, comment);
        recordCommandService.record(instance, "TRANSFER", task.getApprovalNodeId(), message, audit, now);
        try {
            notificationService.closeWorkflowPending(instance, audit, now);
        } catch (Exception ignored) {
            // Notification cleanup is best-effort for a completed transfer.
        }
        try {
            notificationService.createWorkflowPending(instance, List.of(targetUserId), audit, now);
        } catch (Exception ignored) {
            // Notification delivery is best-effort for a completed transfer.
        }
        return currentResponse(taskId, audit);
    }

    @Transactional
    public WorkflowTaskResponse escalate(Long taskId, Long targetUserId, String comment) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowTaskEntity task = requireCurrentPendingTask(taskId);
        LocalDateTime now = audit.now();
        if (task.getDueTime() == null || !task.getDueTime().isBefore(now)) {
            throw new IllegalArgumentException("审批任务尚未超时");
        }
        if (Objects.equals(task.getApproverUserId(), targetUserId)) {
            throw new IllegalArgumentException("升级目标不能是当前处理人");
        }
        WorkflowInstanceEntity instance = requireInstance(task);
        if (Objects.equals(targetUserId, instance.getSubmitUserId())) {
            throw new IllegalArgumentException("升级目标不能是提交人");
        }
        requireActiveTargetUser(targetUserId, audit, "升级目标用户不存在或已停用");

        Long fromUserId = task.getApproverUserId();
        task.setApproverUserId(targetUserId);
        task.setEscalatedTime(now);
        task.setEscalationCount((task.getEscalationCount() == null ? 0 : task.getEscalationCount()) + 1);
        long timeoutHours = approvalConfigService.resolveTaskTimeoutHours(instance, audit, taskTimeoutHours);
        task.setDueTime(now.plusHours(timeoutHours));
        task.setUpdatedBy(audit.userId());
        task.setUpdatedTime(now);
        updateTask(task);

        String message = transitionMessage("超时升级", fromUserId, targetUserId, comment);
        recordCommandService.record(instance, "ESCALATE", task.getApprovalNodeId(), message, audit, now);
        notificationService.closeWorkflowPending(instance, audit, now);
        notificationService.createWorkflowPending(instance, List.of(targetUserId), audit, now);
        return currentResponse(taskId, audit);
    }

    private WorkflowInstanceEntity requireInstance(WorkflowTaskEntity task) {
        WorkflowInstanceEntity instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new IllegalArgumentException("审批实例不存在或已完成");
        }
        return instance;
    }

    private void assertCurrentUserCanTransfer(
            WorkflowInstanceEntity instance,
            WorkflowTaskEntity task,
            AuditMetadata audit
    ) {
        if (task.getApproverUserId() == null) {
            approvalConfigService.assertCurrentUserCanApprove(instance, task.getApprovalNodeId(), audit);
            return;
        }
        if (!Objects.equals(task.getApproverUserId(), audit.userId())) {
            throw new IllegalArgumentException("只能转签自己的待办任务");
        }
    }

    private WorkflowTaskEntity requireCurrentPendingTask(Long taskId) {
        WorkflowTaskEntity task = queryService.requireScopedTask(taskId);
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("审批任务不存在或已完成");
        }
        return task;
    }

    private void requireActiveTargetUser(
            Long targetUserId,
            AuditMetadata audit,
            String errorMessage
    ) {
        UserEntity target = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getId, targetUserId)
                .eq(UserEntity::getCompanyId, audit.companyId())
                .eq(UserEntity::getAccountBookId, audit.accountBookId())
                .eq(UserEntity::getStatus, "ACTIVE")
                .eq(UserEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (target == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void updateTask(WorkflowTaskEntity task) {
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessConflictException("审批任务已被其他操作修改，请刷新后重试");
        }
    }

    private String transitionMessage(String action, Long fromUserId, Long targetUserId, String comment) {
        return action + ": " + fromUserId + " -> " + targetUserId
                + (StringUtils.hasText(comment) ? "；" + comment.trim() : "");
    }

    private WorkflowTaskResponse currentResponse(Long taskId, AuditMetadata audit) {
        return queryService.toTaskResponse(queryService.requireScopedTask(taskId), audit.now());
    }
}

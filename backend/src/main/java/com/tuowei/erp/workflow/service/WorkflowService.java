package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeEntity;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowRecordEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class WorkflowService {

    private static final String STATUS_IN_APPROVAL = "IN_APPROVAL";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_CANCELLED = "CANCELLED";

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowRecordMapper recordMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final NotificationService notificationService;
    private final WorkflowApprovalConfigService approvalConfigService;
    private final WorkflowQueryService queryService;
    private final WorkflowTaskTransitionService taskTransitionService;
    private final WorkflowRecordCommandService recordCommandService;

    @Value("${erp.workflow.task-timeout-hours:24}")
    private long taskTimeoutHours;

    public WorkflowService(
            WorkflowInstanceMapper instanceMapper,
            WorkflowTaskMapper taskMapper,
            WorkflowRecordMapper recordMapper,
            AuditMetadataFactory auditMetadataFactory,
            NotificationService notificationService,
            WorkflowApprovalConfigService approvalConfigService,
            WorkflowQueryService queryService,
            WorkflowTaskTransitionService taskTransitionService,
            WorkflowRecordCommandService recordCommandService
    ) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.notificationService = notificationService;
        this.approvalConfigService = approvalConfigService;
        this.queryService = queryService;
        this.taskTransitionService = taskTransitionService;
        this.recordCommandService = recordCommandService;
    }

    @Transactional
    public void submit(String businessType, Long businessId, String businessNo, String title, String comment) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        closeActiveInstanceIfPresent(businessType, businessId, audit, now, "CANCELLED");

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setCompanyId(audit.companyId());
        instance.setAccountBookId(audit.accountBookId());
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setBusinessNo(businessNo);
        instance.setTitle(title);
        instance.setStatus(STATUS_IN_APPROVAL);
        instance.setSubmitUserId(audit.userId());
        instance.setSubmitTime(now);
        instance.setDeletedFlag(0);
        fillAudit(instance, audit, now);
        instanceMapper.insert(instance);

        WorkflowApprovalNodeEntity firstNode = approvalConfigService.resolveFirstActiveNode(instance, audit);
        Long firstNodeId = firstNode == null ? null : firstNode.getId();
        List<Long> pendingApproverUserIds = approvalConfigService.resolvePendingApproverUserIds(instance, firstNodeId, audit);
        createPendingTask(instance, firstNodeId, pendingApproverUserIds, audit, now);

        recordCommandService.record(instance, "SUBMIT", comment, audit, now);
        notificationService.createWorkflowPending(instance, pendingApproverUserIds, audit, now);
    }

    @Transactional
    public void approve(String businessType, Long businessId, String comment) {
        complete(businessType, businessId, "APPROVED", "APPROVE", comment, null);
    }

    @Transactional
    public void approveTaskForBusiness(Long taskId, String businessType, Long businessId, String comment) {
        WorkflowTaskEntity task = requireMatchingCurrentPendingTask(taskId, businessType, businessId);
        complete(task.getBusinessType(), task.getBusinessId(), "APPROVED", "APPROVE", comment, task.getId());
    }

    @Transactional
    public void reject(String businessType, Long businessId, String comment) {
        complete(businessType, businessId, "REJECTED", "REJECT", comment, null);
    }

    @Transactional
    public void rejectTaskForBusiness(Long taskId, String businessType, Long businessId, String comment) {
        WorkflowTaskEntity task = requireMatchingCurrentPendingTask(taskId, businessType, businessId);
        complete(task.getBusinessType(), task.getBusinessId(), "REJECTED", "REJECT", comment, task.getId());
    }

    @Transactional
    public void cancel(String businessType, Long businessId, String comment) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        WorkflowInstanceEntity instance = findActiveInstance(businessType, businessId, audit);
        if (instance == null) {
            return;
        }
        updateInstanceStatus(instance, "CANCELLED", audit, now);
        closePendingTaskIfPresent(instance, "CANCELLED", audit, now);
        notificationService.closeWorkflowPending(instance, audit, now);
        recordCommandService.record(instance, "CANCEL", comment, audit, now);
    }

    @Transactional
    public void withdraw(String businessType, Long businessId, String comment) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        WorkflowInstanceEntity instance = requireActiveInstance(businessType, businessId, audit);
        if (!Objects.equals(instance.getSubmitUserId(), audit.userId())) {
            throw new IllegalArgumentException("只有提交人可以撤回审批");
        }
        if (hasApprovalActionRecord(instance)) {
            throw new IllegalArgumentException("审批已被处理，不能撤回");
        }
        updateInstanceStatus(instance, STATUS_WITHDRAWN, audit, now);
        closePendingTaskIfPresent(instance, TASK_CANCELLED, audit, now);
        notificationService.closeWorkflowPending(instance, audit, now);
        recordCommandService.record(instance, "WITHDRAW", comment, audit, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowTaskResponse> listTasks(WorkflowTaskPageQuery query) {
        return queryService.listTasks(query);
    }

    @Transactional(readOnly = true)
    public WorkflowTaskResponse taskDetail(Long id) {
        return queryService.taskDetail(id);
    }

    /**
     * 转签：将待办任务审批人改为 targetUserId（仅当前任务处理人可转签）。
     */
    @Transactional
    public WorkflowTaskResponse transfer(Long taskId, Long targetUserId, String comment) {
        return taskTransitionService.transfer(taskId, targetUserId, comment);
    }

    /**
     * 超时升级：将已过截止时间的待办升级给另一位有效用户。
     */
    @Transactional
    public WorkflowTaskResponse escalate(Long taskId, Long targetUserId, String comment) {
        return taskTransitionService.escalate(taskId, targetUserId, comment);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowRecordResponse> listRecords(WorkflowRecordPageQuery query) {
        return queryService.listRecords(query);
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalInfoResponse approvalInfo(String businessType, Long businessId) {
        return queryService.approvalInfo(businessType, businessId);
    }

    private void complete(String businessType, Long businessId, String status, String action, String comment, Long expectedTaskId) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        WorkflowInstanceEntity instance = requireActiveInstance(businessType, businessId, audit);
        if (Objects.equals(instance.getSubmitUserId(), audit.userId())) {
            throw new IllegalArgumentException("提交人不能审批自己的单据");
        }
        WorkflowTaskEntity currentTask = requirePendingTask(instance);
        if (expectedTaskId != null && !Objects.equals(currentTask.getId(), expectedTaskId)) {
            throw new IllegalArgumentException("审批任务不存在或已完成");
        }
        assertCurrentUserCanApprove(instance, currentTask, audit);
        assertCurrentUserHasNotActedOnNode(instance, currentTask, audit);

        recordCommandService.record(instance, action, currentTask.getApprovalNodeId(), comment, audit, now);

        if ("REJECT".equals(action)) {
            closePendingTask(currentTask, status, audit, now);
            notificationService.closeWorkflowPending(instance, audit, now);
            updateInstanceStatus(instance, status, audit, now);
            notificationService.notifyWorkflowResult(instance, action, comment, audit, now);
            return;
        }

        if (currentTask.getApproverUserId() == null
                && approvalConfigService.isAllApprovalMode(instance, currentTask.getApprovalNodeId(), audit)) {
            List<Long> configuredApproverUserIds = approvalConfigService.resolveConfiguredNodeApproverUserIds(
                    instance,
                    currentTask.getApprovalNodeId(),
                    audit
            );
            if (!configuredApproverUserIds.isEmpty()
                    && !allConfiguredApproversApproved(
                    instance,
                    currentTask.getApprovalNodeId(),
                    configuredApproverUserIds
            )) {
                notificationService.closeWorkflowPendingForUser(instance, audit.userId(), audit, now);
                return;
            }
        }

        closePendingTask(currentTask, status, audit, now);
        notificationService.closeWorkflowPending(instance, audit, now);
        if ("APPROVE".equals(action)) {
            WorkflowApprovalNodeEntity nextNode = approvalConfigService.resolveNextActiveNode(
                    instance,
                    currentTask.getApprovalNodeId(),
                    audit
            );
            if (nextNode != null) {
                List<Long> pendingApproverUserIds = approvalConfigService.resolvePendingApproverUserIds(
                        instance,
                        nextNode.getId(),
                        audit
                );
                createPendingTask(instance, nextNode.getId(), pendingApproverUserIds, audit, now);
                notificationService.createWorkflowPending(instance, pendingApproverUserIds, audit, now);
                return;
            }
        }

        updateInstanceStatus(instance, status, audit, now);
        notificationService.notifyWorkflowResult(instance, action, comment, audit, now);
    }

    private void assertCurrentUserCanApprove(
            WorkflowInstanceEntity instance,
            WorkflowTaskEntity currentTask,
            AuditMetadata audit
    ) {
        if (currentTask.getApproverUserId() == null) {
            approvalConfigService.assertCurrentUserCanApprove(instance, currentTask.getApprovalNodeId(), audit);
            return;
        }
        if (!Objects.equals(currentTask.getApproverUserId(), audit.userId())) {
            throw new IllegalArgumentException("当前用户不是该单据审批人");
        }
    }

    private void assertCurrentUserHasNotActedOnNode(
            WorkflowInstanceEntity instance,
            WorkflowTaskEntity currentTask,
            AuditMetadata audit
    ) {
        LambdaQueryWrapper<WorkflowRecordEntity> wrapper = new LambdaQueryWrapper<WorkflowRecordEntity>()
                .eq(WorkflowRecordEntity::getInstanceId, instance.getId())
                .eq(WorkflowRecordEntity::getOperatorUserId, audit.userId())
                .in(WorkflowRecordEntity::getAction, List.of("APPROVE", "REJECT"));
        if (currentTask.getApprovalNodeId() == null) {
            wrapper.isNull(WorkflowRecordEntity::getApprovalNodeId);
        } else {
            wrapper.eq(WorkflowRecordEntity::getApprovalNodeId, currentTask.getApprovalNodeId());
        }
        if (recordMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("当前用户已审批该节点");
        }
    }

    private boolean allConfiguredApproversApproved(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            List<Long> approverUserIds
    ) {
        LambdaQueryWrapper<WorkflowRecordEntity> wrapper = new LambdaQueryWrapper<WorkflowRecordEntity>()
                .eq(WorkflowRecordEntity::getInstanceId, instance.getId())
                .eq(WorkflowRecordEntity::getAction, "APPROVE")
                .in(WorkflowRecordEntity::getOperatorUserId, approverUserIds);
        if (approvalNodeId == null) {
            wrapper.isNull(WorkflowRecordEntity::getApprovalNodeId);
        } else {
            wrapper.eq(WorkflowRecordEntity::getApprovalNodeId, approvalNodeId);
        }
        List<Long> approvedUserIds = recordMapper.selectList(wrapper)
                .stream()
                .map(WorkflowRecordEntity::getOperatorUserId)
                .distinct()
                .toList();
        return approverUserIds.stream().allMatch(approvedUserIds::contains);
    }

    private WorkflowInstanceEntity requireActiveInstance(String businessType, Long businessId, AuditMetadata audit) {
        WorkflowInstanceEntity instance = findActiveInstance(businessType, businessId, audit);
        if (instance == null) {
            throw new IllegalArgumentException("审批实例不存在或已完成");
        }
        return instance;
    }

    private WorkflowInstanceEntity findActiveInstance(String businessType, Long businessId, AuditMetadata audit) {
        return instanceMapper.selectOne(queryService.buildScopedInstanceQuery(audit)
                .eq(WorkflowInstanceEntity::getBusinessType, businessType)
                .eq(WorkflowInstanceEntity::getBusinessId, businessId)
                .eq(WorkflowInstanceEntity::getStatus, STATUS_IN_APPROVAL)
                .last("limit 1"));
    }

    private void closeActiveInstanceIfPresent(
            String businessType,
            Long businessId,
            AuditMetadata audit,
            LocalDateTime now,
            String status
    ) {
        WorkflowInstanceEntity instance = findActiveInstance(businessType, businessId, audit);
        if (instance == null) {
            return;
        }
        updateInstanceStatus(instance, status, audit, now);
        closePendingTaskIfPresent(instance, status, audit, now);
        notificationService.closeWorkflowPending(instance, audit, now);
    }

    private boolean hasApprovalActionRecord(WorkflowInstanceEntity instance) {
        return recordMapper.selectCount(new LambdaQueryWrapper<WorkflowRecordEntity>()
                .eq(WorkflowRecordEntity::getInstanceId, instance.getId())
                .in(WorkflowRecordEntity::getAction, List.of("APPROVE", "REJECT"))) > 0;
    }

    private void createPendingTask(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            List<Long> pendingApproverUserIds,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        WorkflowTaskEntity task = new WorkflowTaskEntity();
        task.setCompanyId(audit.companyId());
        task.setAccountBookId(audit.accountBookId());
        task.setInstanceId(instance.getId());
        task.setBusinessType(instance.getBusinessType());
        task.setBusinessId(instance.getBusinessId());
        task.setBusinessNo(instance.getBusinessNo());
        task.setTitle(instance.getTitle());
        task.setApprovalNodeId(approvalNodeId);
        if (pendingApproverUserIds.size() == 1) {
            task.setApproverUserId(pendingApproverUserIds.get(0));
        }
        task.setStatus(TASK_PENDING);
        task.setDueTime(now.plusHours(approvalConfigService.resolveTaskTimeoutHours(instance, audit, taskTimeoutHours)));
        task.setEscalationCount(0);
        fillAudit(task, audit, now);
        taskMapper.insert(task);
    }

    private void updateInstanceStatus(
            WorkflowInstanceEntity instance,
            String status,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        instance.setStatus(status);
        instance.setCompletedTime(now);
        instance.setUpdatedBy(audit.userId());
        instance.setUpdatedTime(now);
        if (instanceMapper.updateById(instance) != 1) {
            throw new BusinessConflictException("审批实例已被其他操作修改，请刷新后重试");
        }
    }

    private void closePendingTaskIfPresent(
            WorkflowInstanceEntity instance,
            String status,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        WorkflowTaskEntity task = findPendingTask(instance);
        if (task == null) {
            return;
        }
        closePendingTask(task, status, audit, now);
    }

    private WorkflowTaskEntity requirePendingTask(WorkflowInstanceEntity instance) {
        WorkflowTaskEntity task = findPendingTask(instance);
        if (task == null) {
            throw new IllegalArgumentException("审批任务不存在或已完成");
        }
        return task;
    }

    private WorkflowTaskEntity requireCurrentPendingTask(Long id) {
        WorkflowTaskEntity task = queryService.requireScopedTask(id);
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new IllegalArgumentException("审批任务不存在或已完成");
        }
        return task;
    }

    private WorkflowTaskEntity requireMatchingCurrentPendingTask(Long id, String businessType, Long businessId) {
        WorkflowTaskEntity task = requireCurrentPendingTask(id);
        if (!Objects.equals(task.getBusinessType(), businessType) || !Objects.equals(task.getBusinessId(), businessId)) {
            throw new IllegalArgumentException("审批任务与业务单据不匹配");
        }
        return task;
    }

    private WorkflowTaskEntity findPendingTask(WorkflowInstanceEntity instance) {
        return taskMapper.selectOne(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getInstanceId, instance.getId())
                .eq(WorkflowTaskEntity::getStatus, TASK_PENDING)
                .orderByDesc(WorkflowTaskEntity::getCreatedTime)
                .orderByDesc(WorkflowTaskEntity::getId)
                .last("limit 1"));
    }

    private void closePendingTask(
            WorkflowTaskEntity task,
            String status,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        task.setApproverUserId(audit.userId());
        task.setStatus(status);
        task.setUpdatedBy(audit.userId());
        task.setUpdatedTime(now);
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessConflictException("审批任务已被其他操作修改，请刷新后重试");
        }
    }

    private void fillAudit(WorkflowInstanceEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillAudit(WorkflowTaskEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

}

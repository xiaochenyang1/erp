package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeEntity;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowRecordEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import com.tuowei.erp.workflow.web.WorkflowApprovalRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private final SystemLogService systemLogService;
    private final CurrentUserContext currentUserContext;
    private final NotificationService notificationService;
    private final WorkflowApprovalConfigService approvalConfigService;
    private final UserMapper userMapper;

    @Value("${erp.workflow.task-timeout-hours:24}")
    private long taskTimeoutHours;

    public WorkflowService(
            WorkflowInstanceMapper instanceMapper,
            WorkflowTaskMapper taskMapper,
            WorkflowRecordMapper recordMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemLogService systemLogService,
            CurrentUserContext currentUserContext,
            NotificationService notificationService,
            WorkflowApprovalConfigService approvalConfigService,
            UserMapper userMapper
    ) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemLogService = systemLogService;
        this.currentUserContext = currentUserContext;
        this.notificationService = notificationService;
        this.approvalConfigService = approvalConfigService;
        this.userMapper = userMapper;
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
        createPendingTask(instance, firstNodeId, audit, now);

        insertRecord(instance, "SUBMIT", comment, audit, now);
        List<Long> pendingApproverUserIds = approvalConfigService.resolvePendingApproverUserIds(instance, firstNodeId, audit);
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
        insertRecord(instance, "CANCEL", comment, audit, now);
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
        insertRecord(instance, "WITHDRAW", comment, audit, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowTaskResponse> listTasks(WorkflowTaskPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowTaskPageQuery safeQuery = safeQuery(query);
        Page<WorkflowTaskEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        Page<WorkflowTaskEntity> result = taskMapper.selectPage(page, buildTaskQuery(safeQuery, audit));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(task -> toTaskResponse(task, audit.now())).toList()
        );
    }

    @Transactional(readOnly = true)
    public WorkflowTaskResponse taskDetail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toTaskResponse(requireScopedTask(id), audit.now());
    }

    /**
     * 转签：将待办任务审批人改为 targetUserId（仅当前任务处理人可转签）。
     */
    @Transactional
    public WorkflowTaskResponse transfer(Long taskId, Long targetUserId, String comment) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowTaskEntity task = requireCurrentPendingTask(taskId);
        if (!Objects.equals(task.getApproverUserId(), audit.userId())) {
            throw new IllegalArgumentException("只能转签自己的待办任务");
        }
        if (Objects.equals(targetUserId, audit.userId())) {
            throw new IllegalArgumentException("不能转签给自己");
        }
        LocalDateTime now = audit.now();
        Long fromUserId = task.getApproverUserId();
        task.setApproverUserId(targetUserId);
        task.setUpdatedBy(audit.userId());
        task.setUpdatedTime(now);
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessConflictException("审批任务已被其他操作修改，请刷新后重试");
        }
        WorkflowInstanceEntity instance = instanceMapper.selectById(task.getInstanceId());
        if (instance != null) {
            String msg = "转签: " + fromUserId + " -> " + targetUserId
                    + (StringUtils.hasText(comment) ? "；" + comment.trim() : "");
            insertRecord(instance, "TRANSFER", task.getApprovalNodeId(), msg, audit, now);
            try {
                notificationService.createWorkflowPending(instance, List.of(targetUserId), audit, now);
            } catch (Exception ignored) {
                // 通知失败不阻断转签
            }
        }
        return toTaskResponse(requireScopedTask(taskId), audit.now());
    }

    /**
     * 超时升级：将已过截止时间的待办升级给另一位有效用户。
     */
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
        UserEntity target = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getId, targetUserId)
                .eq(UserEntity::getCompanyId, audit.companyId())
                .eq(UserEntity::getAccountBookId, audit.accountBookId())
                .eq(UserEntity::getStatus, "ACTIVE")
                .eq(UserEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (target == null) {
            throw new IllegalArgumentException("升级目标用户不存在或已停用");
        }
        Long fromUserId = task.getApproverUserId();
        task.setApproverUserId(targetUserId);
        task.setEscalatedTime(now);
        task.setEscalationCount((task.getEscalationCount() == null ? 0 : task.getEscalationCount()) + 1);
        task.setDueTime(now.plusHours(Math.max(taskTimeoutHours, 1)));
        task.setUpdatedBy(audit.userId());
        task.setUpdatedTime(now);
        if (taskMapper.updateById(task) != 1) {
            throw new BusinessConflictException("审批任务已被其他操作修改，请刷新后重试");
        }
        WorkflowInstanceEntity instance = instanceMapper.selectById(task.getInstanceId());
        if (instance != null) {
            String message = "超时升级: " + fromUserId + " -> " + targetUserId
                    + (StringUtils.hasText(comment) ? "；" + comment.trim() : "");
            insertRecord(instance, "ESCALATE", task.getApprovalNodeId(), message, audit, now);
            notificationService.createWorkflowPending(instance, List.of(targetUserId), audit, now);
        }
        return toTaskResponse(requireScopedTask(taskId), audit.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowRecordResponse> listRecords(WorkflowRecordPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowRecordPageQuery safeQuery = safeQuery(query);
        Page<WorkflowRecordEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        Page<WorkflowRecordEntity> result = recordMapper.selectPage(page, buildRecordQuery(safeQuery, audit));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRecordResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalInfoResponse approvalInfo(String businessType, Long businessId) {
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowInstanceEntity instance = instanceMapper.selectOne(buildScopedInstanceQuery(audit)
                .eq(WorkflowInstanceEntity::getBusinessType, businessType)
                .eq(WorkflowInstanceEntity::getBusinessId, businessId)
                .orderByDesc(WorkflowInstanceEntity::getSubmitTime)
                .orderByDesc(WorkflowInstanceEntity::getId)
                .last("limit 1"));
        if (instance == null) {
            return new WorkflowApprovalInfoResponse(null, "NOT_SUBMITTED", null, null, null, List.of());
        }
        List<WorkflowApprovalRecordResponse> records = recordMapper.selectList(new LambdaQueryWrapper<WorkflowRecordEntity>()
                        .eq(WorkflowRecordEntity::getInstanceId, instance.getId())
                        .orderByAsc(WorkflowRecordEntity::getActionTime)
                        .orderByAsc(WorkflowRecordEntity::getId))
                .stream()
                .map(this::toApprovalRecordResponse)
                .toList();
        return new WorkflowApprovalInfoResponse(
                instance.getId(),
                instance.getStatus(),
                instance.getSubmitUserId(),
                instance.getSubmitTime(),
                instance.getCompletedTime(),
                records
        );
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
        approvalConfigService.assertCurrentUserCanApprove(instance, currentTask.getApprovalNodeId(), audit);
        assertCurrentUserHasNotActedOnNode(instance, currentTask, audit);

        insertRecord(instance, action, currentTask.getApprovalNodeId(), comment, audit, now);

        if ("REJECT".equals(action)) {
            closePendingTask(currentTask, status, audit, now);
            notificationService.closeWorkflowPending(instance, audit, now);
            updateInstanceStatus(instance, status, audit, now);
            notificationService.notifyWorkflowResult(instance, action, comment, audit, now);
            return;
        }

        List<Long> configuredApproverUserIds = approvalConfigService.resolveConfiguredNodeApproverUserIds(
                instance,
                currentTask.getApprovalNodeId(),
                audit
        );
        if (approvalConfigService.isAllApprovalMode(instance, currentTask.getApprovalNodeId(), audit)
                && !configuredApproverUserIds.isEmpty()
                && !allConfiguredApproversApproved(instance, currentTask.getApprovalNodeId(), configuredApproverUserIds)) {
            notificationService.closeWorkflowPendingForUser(instance, audit.userId(), audit, now);
            return;
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
                createPendingTask(instance, nextNode.getId(), audit, now);
                List<Long> pendingApproverUserIds = approvalConfigService.resolvePendingApproverUserIds(
                        instance,
                        nextNode.getId(),
                        audit
                );
                notificationService.createWorkflowPending(instance, pendingApproverUserIds, audit, now);
                return;
            }
        }

        updateInstanceStatus(instance, status, audit, now);
        notificationService.notifyWorkflowResult(instance, action, comment, audit, now);
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
        return instanceMapper.selectOne(buildScopedInstanceQuery(audit)
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
        task.setStatus(TASK_PENDING);
        task.setDueTime(now.plusHours(Math.max(taskTimeoutHours, 1)));
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

    private WorkflowTaskEntity requireScopedTask(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        WorkflowTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<WorkflowTaskEntity>()
                .eq(WorkflowTaskEntity::getId, id)
                .inSql(WorkflowTaskEntity::getInstanceId, scopedInstanceIdSubQuery(audit))
                .last("limit 1"));
        if (task == null) {
            throw new IllegalArgumentException("审批任务不存在");
        }
        return task;
    }

    private WorkflowTaskEntity requireCurrentPendingTask(Long id) {
        WorkflowTaskEntity task = requireScopedTask(id);
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

    private void insertRecord(WorkflowInstanceEntity instance, String action, String comment, AuditMetadata audit, LocalDateTime now) {
        insertRecord(instance, action, null, comment, audit, now);
    }

    private void insertRecord(
            WorkflowInstanceEntity instance,
            String action,
            Long approvalNodeId,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        WorkflowRecordEntity record = new WorkflowRecordEntity();
        record.setCompanyId(audit.companyId());
        record.setAccountBookId(audit.accountBookId());
        record.setInstanceId(instance.getId());
        record.setBusinessType(instance.getBusinessType());
        record.setBusinessId(instance.getBusinessId());
        record.setBusinessNo(instance.getBusinessNo());
        record.setAction(action);
        record.setApprovalNodeId(approvalNodeId);
        record.setOperatorUserId(audit.userId());
        record.setComment(comment);
        record.setActionTime(now);
        fillAudit(record, audit, now);
        recordMapper.insert(record);
        systemLogService.recordAudit(
                "WORKFLOW",
                instance.getBusinessType(),
                instance.getBusinessId(),
                instance.getBusinessNo(),
                action,
                audit.userId(),
                currentOperatorName(),
                null,
                comment,
                now
        );
    }

    private LambdaQueryWrapper<WorkflowTaskEntity> buildTaskQuery(WorkflowTaskPageQuery query, AuditMetadata audit) {
        LambdaQueryWrapper<WorkflowTaskEntity> wrapper = new LambdaQueryWrapper<WorkflowTaskEntity>()
                .inSql(WorkflowTaskEntity::getInstanceId, scopedInstanceIdSubQuery(audit));
        String businessType = normalizeNullable(query.getBusinessType());
        if (StringUtils.hasText(businessType)) {
            wrapper.eq(WorkflowTaskEntity::getBusinessType, businessType.toUpperCase(Locale.ROOT));
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(WorkflowTaskEntity::getBusinessId, query.getBusinessId());
        }
        String businessNo = normalizeNullable(query.getBusinessNo());
        if (StringUtils.hasText(businessNo)) {
            wrapper.eq(WorkflowTaskEntity::getBusinessNo, businessNo);
        }
        String status = normalizeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(WorkflowTaskEntity::getStatus, status.toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(WorkflowTaskEntity::getCreatedTime).orderByDesc(WorkflowTaskEntity::getId);
    }

    private LambdaQueryWrapper<WorkflowRecordEntity> buildRecordQuery(WorkflowRecordPageQuery query, AuditMetadata audit) {
        LambdaQueryWrapper<WorkflowRecordEntity> wrapper = new LambdaQueryWrapper<WorkflowRecordEntity>()
                .inSql(WorkflowRecordEntity::getInstanceId, scopedInstanceIdSubQuery(audit));
        String businessType = normalizeNullable(query.getBusinessType());
        if (StringUtils.hasText(businessType)) {
            wrapper.eq(WorkflowRecordEntity::getBusinessType, businessType.toUpperCase(Locale.ROOT));
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(WorkflowRecordEntity::getBusinessId, query.getBusinessId());
        }
        String businessNo = normalizeNullable(query.getBusinessNo());
        if (StringUtils.hasText(businessNo)) {
            wrapper.eq(WorkflowRecordEntity::getBusinessNo, businessNo);
        }
        String action = normalizeNullable(query.getAction());
        if (StringUtils.hasText(action)) {
            wrapper.eq(WorkflowRecordEntity::getAction, action.toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(WorkflowRecordEntity::getActionTime).orderByDesc(WorkflowRecordEntity::getId);
    }

    private LambdaQueryWrapper<WorkflowInstanceEntity> buildScopedInstanceQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<WorkflowInstanceEntity>()
                .eq(WorkflowInstanceEntity::getDeletedFlag, 0)
                .eq(WorkflowInstanceEntity::getCompanyId, audit.companyId())
                .eq(WorkflowInstanceEntity::getAccountBookId, audit.accountBookId());
    }

    private String scopedInstanceIdSubQuery(AuditMetadata audit) {
        return "select id from wf_approval_instance where deleted_flag = 0"
                + " and company_id = " + audit.companyId()
                + " and account_book_id = " + audit.accountBookId();
    }

    private WorkflowTaskPageQuery safeQuery(WorkflowTaskPageQuery query) {
        return query == null ? new WorkflowTaskPageQuery() : query;
    }

    private WorkflowRecordPageQuery safeQuery(WorkflowRecordPageQuery query) {
        return query == null ? new WorkflowRecordPageQuery() : query;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentOperatorName() {
        return currentUserContext.requireCurrentUser().username();
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private WorkflowTaskResponse toTaskResponse(WorkflowTaskEntity entity, LocalDateTime now) {
        return new WorkflowTaskResponse(
                entity.getId(),
                entity.getInstanceId(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getTitle(),
                entity.getApproverUserId(),
                entity.getStatus(),
                entity.getDueTime(),
                TASK_PENDING.equals(entity.getStatus()) && entity.getDueTime() != null && entity.getDueTime().isBefore(now),
                entity.getEscalatedTime(),
                entity.getEscalationCount(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }

    private WorkflowRecordResponse toRecordResponse(WorkflowRecordEntity entity) {
        return new WorkflowRecordResponse(
                entity.getId(),
                entity.getInstanceId(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getAction(),
                entity.getOperatorUserId(),
                entity.getComment(),
                entity.getActionTime()
        );
    }

    private WorkflowApprovalRecordResponse toApprovalRecordResponse(WorkflowRecordEntity entity) {
        return new WorkflowApprovalRecordResponse(
                entity.getId(),
                entity.getAction(),
                entity.getOperatorUserId(),
                entity.getComment(),
                entity.getActionTime()
        );
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

    private void fillAudit(WorkflowRecordEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

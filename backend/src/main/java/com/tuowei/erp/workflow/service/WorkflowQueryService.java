package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Read-side scoping, detail loading and response mapping for workflow approval tasks and records. */
@Service
public class WorkflowQueryService {

    private static final String TASK_PENDING = "PENDING";

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowRecordMapper recordMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public WorkflowQueryService(
            WorkflowInstanceMapper instanceMapper,
            WorkflowTaskMapper taskMapper,
            WorkflowRecordMapper recordMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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

    /**
     * Loads a workflow task scoped to the current tenant (company + account book).
     * Shared by the read side ({@link #taskDetail}) and the posting service write guards.
     */
    @Transactional(readOnly = true)
    public WorkflowTaskEntity requireScopedTask(Long id) {
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

    LambdaQueryWrapper<WorkflowInstanceEntity> buildScopedInstanceQuery(AuditMetadata audit) {
        return new LambdaQueryWrapper<WorkflowInstanceEntity>()
                .eq(WorkflowInstanceEntity::getDeletedFlag, 0)
                .eq(WorkflowInstanceEntity::getCompanyId, audit.companyId())
                .eq(WorkflowInstanceEntity::getAccountBookId, audit.accountBookId());
    }

    String scopedInstanceIdSubQuery(AuditMetadata audit) {
        return "select id from wf_approval_instance where deleted_flag = 0"
                + " and company_id = " + audit.companyId()
                + " and account_book_id = " + audit.accountBookId();
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
        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            wrapper.eq(WorkflowTaskEntity::getStatus, TASK_PENDING)
                    .isNotNull(WorkflowTaskEntity::getDueTime)
                    .lt(WorkflowTaskEntity::getDueTime, audit.now());
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

    private WorkflowTaskPageQuery safeQuery(WorkflowTaskPageQuery query) {
        return query == null ? new WorkflowTaskPageQuery() : query;
    }

    private WorkflowRecordPageQuery safeQuery(WorkflowRecordPageQuery query) {
        return query == null ? new WorkflowRecordPageQuery() : query;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    WorkflowTaskResponse toTaskResponse(WorkflowTaskEntity entity, LocalDateTime now) {
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
}

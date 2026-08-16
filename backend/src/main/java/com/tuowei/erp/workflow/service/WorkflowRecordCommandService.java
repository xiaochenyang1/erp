package com.tuowei.erp.workflow.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.model.WorkflowRecordEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Writes workflow action history and its matching system audit entry. */
@Service
public class WorkflowRecordCommandService {

    private final WorkflowRecordMapper recordMapper;
    private final SystemLogService systemLogService;
    private final CurrentUserContext currentUserContext;

    public WorkflowRecordCommandService(
            WorkflowRecordMapper recordMapper,
            SystemLogService systemLogService,
            CurrentUserContext currentUserContext
    ) {
        this.recordMapper = recordMapper;
        this.systemLogService = systemLogService;
        this.currentUserContext = currentUserContext;
    }

    public void record(
            WorkflowInstanceEntity instance,
            String action,
            String comment,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        record(instance, action, null, comment, audit, now);
    }

    public void record(
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
                currentUserContext.requireCurrentUser().username(),
                null,
                comment,
                now
        );
    }

    private void fillAudit(WorkflowRecordEntity record, AuditMetadata audit, LocalDateTime now) {
        record.setCreatedBy(audit.userId());
        record.setCreatedTime(now);
        record.setUpdatedBy(audit.userId());
        record.setUpdatedTime(now);
        record.setVersion(0);
    }
}

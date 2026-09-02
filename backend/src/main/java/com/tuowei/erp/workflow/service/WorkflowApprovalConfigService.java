package com.tuowei.erp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalConfigMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeApproverMapper;
import com.tuowei.erp.workflow.mapper.WorkflowApprovalNodeMapper;
import com.tuowei.erp.workflow.model.WorkflowApprovalConfigEntity;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeApproverEntity;
import com.tuowei.erp.workflow.model.WorkflowApprovalNodeEntity;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import com.tuowei.erp.workflow.web.WorkflowApprovalApproverRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigRequest;
import com.tuowei.erp.workflow.web.WorkflowApprovalConfigResponse;
import com.tuowei.erp.workflow.web.WorkflowApprovalNodeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class WorkflowApprovalConfigService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String APPROVER_USER = "USER";
    private static final String APPROVER_ROLE = "ROLE";

    private final WorkflowApprovalConfigMapper configMapper;
    private final WorkflowApprovalNodeMapper nodeMapper;
    private final WorkflowApprovalNodeApproverMapper approverMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WorkflowApprovalConfigQueryService queryService;

    public WorkflowApprovalConfigService(
            WorkflowApprovalConfigMapper configMapper,
            WorkflowApprovalNodeMapper nodeMapper,
            WorkflowApprovalNodeApproverMapper approverMapper,
            AuditMetadataFactory auditMetadataFactory,
            WorkflowApprovalConfigQueryService queryService
    ) {
        this.configMapper = configMapper;
        this.nodeMapper = nodeMapper;
        this.approverMapper = approverMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalConfigResponse getByBusinessType(String businessType) {
        return queryService.getByBusinessType(businessType);
    }

    @Transactional
    public WorkflowApprovalConfigResponse save(String businessType, WorkflowApprovalConfigRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String normalizedBusinessType = queryService.normalizeCode(businessType);
        WorkflowApprovalConfigEntity config = queryService.findConfig(audit, normalizedBusinessType);
        if (config == null) {
            config = new WorkflowApprovalConfigEntity();
            config.setCompanyId(audit.companyId());
            config.setAccountBookId(audit.accountBookId());
            config.setBusinessType(normalizedBusinessType);
            config.setDeletedFlag(0);
            config.setCreatedBy(audit.userId());
            config.setCreatedTime(now);
            config.setVersion(0);
        }
        config.setConfigName(request.configName().trim());
        config.setStatus(normalizeStatus(request.status()));
        config.setTaskTimeoutHours(queryService.normalizeTaskTimeoutHours(request.taskTimeoutHours()));
        config.setRemark(normalizeNullableText(request.remark()));
        config.setUpdatedBy(audit.userId());
        config.setUpdatedTime(now);

        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            OptimisticLockGuard.requireUpdated(
                    configMapper.updateById(config),
                    "审批配置已被其他操作修改，请刷新后重试"
            );
        }
        replaceNodes(config.getId(), audit, now, request.nodes());
        return queryService.toResponse(config);
    }

    public WorkflowApprovalNodeEntity resolveFirstActiveNode(WorkflowInstanceEntity instance, AuditMetadata audit) {
        return queryService.resolveFirstActiveNode(instance, audit);
    }

    public WorkflowApprovalNodeEntity resolveNextActiveNode(
            WorkflowInstanceEntity instance,
            Long currentNodeId,
            AuditMetadata audit
    ) {
        return queryService.resolveNextActiveNode(instance, currentNodeId, audit);
    }

    public List<Long> resolvePendingApproverUserIds(WorkflowInstanceEntity instance, AuditMetadata audit) {
        return queryService.resolvePendingApproverUserIds(instance, audit);
    }

    public List<Long> resolvePendingApproverUserIds(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        return queryService.resolvePendingApproverUserIds(instance, approvalNodeId, audit);
    }

    public List<Long> resolveConfiguredNodeApproverUserIds(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        return queryService.resolveConfiguredNodeApproverUserIds(instance, approvalNodeId, audit);
    }

    public boolean isAllApprovalMode(WorkflowInstanceEntity instance, Long approvalNodeId, AuditMetadata audit) {
        return queryService.isAllApprovalMode(instance, approvalNodeId, audit);
    }

    public long resolveTaskTimeoutHours(WorkflowInstanceEntity instance, AuditMetadata audit, long fallbackHours) {
        return queryService.resolveTaskTimeoutHours(instance, audit, fallbackHours);
    }

    public void assertCurrentUserCanApprove(WorkflowInstanceEntity instance, AuditMetadata audit) {
        queryService.assertCurrentUserCanApprove(instance, audit);
    }

    public void assertCurrentUserCanApprove(
            WorkflowInstanceEntity instance,
            Long approvalNodeId,
            AuditMetadata audit
    ) {
        queryService.assertCurrentUserCanApprove(instance, approvalNodeId, audit);
    }

    private void replaceNodes(
            Long configId,
            AuditMetadata audit,
            LocalDateTime now,
            List<WorkflowApprovalNodeRequest> nodeRequests
    ) {
        List<Long> existingNodeIds = nodeMapper.selectList(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                        .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                        .eq(WorkflowApprovalNodeEntity::getConfigId, configId))
                .stream()
                .map(WorkflowApprovalNodeEntity::getId)
                .toList();
        if (!existingNodeIds.isEmpty()) {
            approverMapper.delete(new LambdaQueryWrapper<WorkflowApprovalNodeApproverEntity>()
                    .eq(WorkflowApprovalNodeApproverEntity::getCompanyId, audit.companyId())
                    .in(WorkflowApprovalNodeApproverEntity::getNodeId, existingNodeIds));
        }
        nodeMapper.delete(new LambdaQueryWrapper<WorkflowApprovalNodeEntity>()
                .eq(WorkflowApprovalNodeEntity::getCompanyId, audit.companyId())
                .eq(WorkflowApprovalNodeEntity::getConfigId, configId));

        int index = 1;
        for (WorkflowApprovalNodeRequest nodeRequest : nodeRequests) {
            WorkflowApprovalNodeEntity node = new WorkflowApprovalNodeEntity();
            node.setCompanyId(audit.companyId());
            node.setConfigId(configId);
            node.setNodeName(nodeRequest.nodeName().trim());
            node.setNodeOrder(nodeRequest.nodeOrder() == null ? index : nodeRequest.nodeOrder());
            node.setApprovalMode(normalizeApprovalMode(nodeRequest.approvalMode()));
            node.setStatus(STATUS_ACTIVE);
            node.setCreatedBy(audit.userId());
            node.setCreatedTime(now);
            node.setUpdatedBy(audit.userId());
            node.setUpdatedTime(now);
            node.setVersion(0);
            nodeMapper.insert(node);
            for (WorkflowApprovalApproverRequest approverRequest : nodeRequest.approvers()) {
                WorkflowApprovalNodeApproverEntity approver = new WorkflowApprovalNodeApproverEntity();
                approver.setCompanyId(audit.companyId());
                approver.setNodeId(node.getId());
                approver.setApproverType(normalizeApproverType(approverRequest.approverType()));
                approver.setApproverId(approverRequest.approverId());
                approver.setCreatedBy(audit.userId());
                approver.setCreatedTime(now);
                approver.setUpdatedBy(audit.userId());
                approver.setUpdatedTime(now);
                approver.setVersion(0);
                approverMapper.insert(approver);
            }
            index++;
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : STATUS_ACTIVE;
        if (!STATUS_ACTIVE.equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("审批配置状态无效");
        }
        return normalized;
    }

    private String normalizeApprovalMode(String approvalMode) {
        String normalized = StringUtils.hasText(approvalMode)
                ? approvalMode.trim().toUpperCase(Locale.ROOT)
                : "ANY";
        if (!"ANY".equals(normalized) && !"ALL".equals(normalized)) {
            throw new IllegalArgumentException("审批模式无效");
        }
        return normalized;
    }

    private String normalizeApproverType(String approverType) {
        String normalized = queryService.normalizeCode(approverType);
        if (!APPROVER_USER.equals(normalized) && !APPROVER_ROLE.equals(normalized)) {
            throw new IllegalArgumentException("审批人类型无效");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

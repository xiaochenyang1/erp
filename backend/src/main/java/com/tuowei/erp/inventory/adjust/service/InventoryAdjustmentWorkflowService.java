package com.tuowei.erp.inventory.adjust.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Approval changes document state only. Stock and vouchers are written by the separate posting action. */
@Service
public class InventoryAdjustmentWorkflowService {
    public static final String BUSINESS_TYPE = "INVENTORY_ADJUSTMENT";

    private final InventoryAdjustmentMapper adjustmentMapper;
    private final InventoryAdjustmentQueryService queryService;
    private final WorkflowService workflowService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentService attachmentService;

    public InventoryAdjustmentWorkflowService(InventoryAdjustmentMapper adjustmentMapper,
                                              InventoryAdjustmentQueryService queryService,
                                              WorkflowService workflowService,
                                              AuditMetadataFactory auditMetadataFactory,
                                              AttachmentService attachmentService) {
        this.adjustmentMapper = adjustmentMapper;
        this.queryService = queryService;
        this.workflowService = workflowService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentService = attachmentService;
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_SUBMIT)
    public InventoryAdjustmentResponse submit(Long id, String comment) {
        InventoryAdjustmentEntity adjustment = queryService.requireAdjustment(id);
        if (!"DRAFT".equals(adjustment.getStatus()) && !"REJECTED".equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的库存调整单可以提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.INVENTORY_ADJUSTMENT, id);
        transition(adjustment, "SUBMITTED");
        workflowService.submit(BUSINESS_TYPE, id, adjustment.getAdjustmentNo(),
                "库存调整单 " + adjustment.getAdjustmentNo(), comment);
        return queryService.getById(id);
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_APPROVE)
    public InventoryAdjustmentResponse approve(Long id, String comment) {
        return approve(id, comment, null);
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_APPROVE)
    public InventoryAdjustmentResponse approveWorkflowTask(Long taskId, Long id, String comment) {
        return approve(id, comment, taskId);
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_APPROVE)
    public InventoryAdjustmentResponse reject(Long id, String comment) {
        return reject(id, comment, null);
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_APPROVE)
    public InventoryAdjustmentResponse rejectWorkflowTask(Long taskId, Long id, String comment) {
        return reject(id, comment, taskId);
    }

    @Transactional
    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_SUBMIT)
    public InventoryAdjustmentResponse withdraw(Long id, String comment) {
        InventoryAdjustmentEntity adjustment = requireSubmitted(id);
        workflowService.withdraw(BUSINESS_TYPE, id, comment);
        transition(adjustment, "DRAFT");
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public WorkflowApprovalInfoResponse approvalInfo(Long id) {
        queryService.requireAdjustment(id);
        return workflowService.approvalHistory(BUSINESS_TYPE, id);
    }

    private InventoryAdjustmentResponse approve(Long id, String comment, Long taskId) {
        InventoryAdjustmentEntity adjustment = requireSubmitted(id);
        boolean completed = taskId == null
                ? workflowService.approve(BUSINESS_TYPE, id, comment)
                : workflowService.approveTaskForBusiness(taskId, BUSINESS_TYPE, id, comment);
        if (completed) transition(adjustment, "APPROVED");
        return queryService.getById(id);
    }

    private InventoryAdjustmentResponse reject(Long id, String comment, Long taskId) {
        InventoryAdjustmentEntity adjustment = requireSubmitted(id);
        if (!StringUtils.hasText(comment) || comment.length() > 255) {
            throw new IllegalArgumentException("请填写不超过255字的驳回原因");
        }
        if (taskId == null) workflowService.reject(BUSINESS_TYPE, id, comment.trim());
        else workflowService.rejectTaskForBusiness(taskId, BUSINESS_TYPE, id, comment.trim());
        transition(adjustment, "REJECTED");
        return queryService.getById(id);
    }

    private InventoryAdjustmentEntity requireSubmitted(Long id) {
        InventoryAdjustmentEntity adjustment = queryService.requireAdjustment(id);
        if (!"SUBMITTED".equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("只有待审批的库存调整单可以处理审批");
        }
        return adjustment;
    }

    private void transition(InventoryAdjustmentEntity adjustment, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        adjustment.setStatus(status);
        adjustment.setUpdatedBy(audit.userId());
        adjustment.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(adjustmentMapper.updateById(adjustment),
                "库存调整单已被其他操作修改，请刷新后重试");
    }
}

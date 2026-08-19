package com.tuowei.erp.purchase.order.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Approval workflow and lifecycle transitions for purchase orders. */
@Service
public class PurchaseOrderWorkflowService {

    private static final String BUSINESS_TYPE = "PURCHASE_ORDER";

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final WorkflowService workflowService;
    private final PurchasePriceEvaluator purchasePriceEvaluator;
    private final AttachmentService attachmentService;

    public PurchaseOrderWorkflowService(
            PurchaseOrderMapper purchaseOrderMapper,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderQueryService purchaseOrderQueryService,
            WorkflowService workflowService,
            PurchasePriceEvaluator purchasePriceEvaluator,
            AttachmentService attachmentService
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderQueryService = purchaseOrderQueryService;
        this.workflowService = workflowService;
        this.purchasePriceEvaluator = purchasePriceEvaluator;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public PurchaseOrderResponse submit(Long id, PurchaseOrderSubmitRequest request) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.PURCHASE_ORDER, entity.getId());
        List<PurchaseOrderLineEntity> existingLines = purchaseOrderQueryService.selectLines(entity);
        List<PurchaseOrderLineRequest> lineRequests = existingLines.stream()
                .map(line -> new PurchaseOrderLineRequest(
                        line.getProductId(),
                        line.getQty(),
                        line.getPrice(),
                        line.getTaxRate(),
                        line.getRemark()
                ))
                .toList();
        purchasePriceEvaluator.assertLinesWithinMaxPrice(
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getSupplierId(),
                entity.getOrderDate(),
                lineRequests
        );
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "SUBMITTED", "IN_APPROVAL");
        workflowService.submit(
                BUSINESS_TYPE,
                entity.getId(),
                entity.getOrderNo(),
                "采购订单 " + entity.getOrderNo(),
                request.remark()
        );
        return response;
    }

    @Transactional
    public PurchaseOrderResponse approve(Long id, PurchaseOrderApproveRequest request) {
        return approve(id, request, null);
    }

    @Transactional
    public PurchaseOrderResponse approveWorkflowTask(
            Long taskId,
            Long id,
            PurchaseOrderApproveRequest request
    ) {
        return approve(id, request, taskId);
    }

    @Transactional
    public PurchaseOrderResponse unapprove(Long id) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"APPROVED".equals(entity.getStatus()) || !"APPROVED".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许反审核");
        }
        if (!"NOT_RECEIVED".equals(entity.getReceiptStatus())) {
            throw new IllegalArgumentException("已入库采购订单不允许反审核");
        }
        return transitionWorkflowStatus(entity, "DRAFT", "NOT_SUBMITTED");
    }

    @Transactional
    public PurchaseOrderResponse reject(Long id, PurchaseOrderRejectRequest request) {
        return reject(id, request, null);
    }

    @Transactional
    public PurchaseOrderResponse rejectWorkflowTask(
            Long taskId,
            Long id,
            PurchaseOrderRejectRequest request
    ) {
        return reject(id, request, taskId);
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())
                && !"SUBMITTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许作废");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel(BUSINESS_TYPE, entity.getId(), "作废采购订单");
        return response;
    }

    @Transactional
    public PurchaseOrderResponse close(Long id) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许关闭");
        }
        if ("RECEIVED".equals(entity.getReceiptStatus())) {
            throw new IllegalArgumentException("已完全入库的采购订单不允许关闭");
        }
        return transitionWorkflowStatus(entity, "CLOSED", "APPROVED");
    }

    private PurchaseOrderResponse approve(
            Long id,
            PurchaseOrderApproveRequest request,
            Long workflowTaskId
    ) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许审批通过");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "APPROVED", "APPROVED");
        if (workflowTaskId == null) {
            workflowService.approve(BUSINESS_TYPE, entity.getId(), request.remark());
        } else {
            workflowService.approveTaskForBusiness(
                    workflowTaskId,
                    BUSINESS_TYPE,
                    entity.getId(),
                    request.remark()
            );
        }
        return response;
    }

    private PurchaseOrderResponse reject(
            Long id,
            PurchaseOrderRejectRequest request,
            Long workflowTaskId
    ) {
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许驳回");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject(BUSINESS_TYPE, entity.getId(), request.reason());
        } else {
            workflowService.rejectTaskForBusiness(
                    workflowTaskId,
                    BUSINESS_TYPE,
                    entity.getId(),
                    request.reason()
            );
        }
        return response;
    }

    private PurchaseOrderResponse transitionWorkflowStatus(
            PurchaseOrderEntity entity,
            String status,
            String approvalStatus
    ) {
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                purchaseOrderMapper.updateById(entity),
                "采购订单已被其他操作修改，请刷新后重试"
        );
        return purchaseOrderQueryService.getById(entity.getId());
    }
}

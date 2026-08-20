package com.tuowei.erp.sales.order.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Approval workflow and lifecycle transitions for sales orders. */
@Service
public class SalesOrderWorkflowService {

    private static final String BUSINESS_TYPE = "SALES_ORDER";

    private final SalesOrderMapper salesOrderMapper;
    private final CustomerMapper customerMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesOrderQueryService salesOrderQueryService;
    private final WorkflowService workflowService;
    private final SalesCreditEvaluator salesCreditEvaluator;
    private final SalesPriceEvaluator salesPriceEvaluator;
    private final AttachmentService attachmentService;

    public SalesOrderWorkflowService(
            SalesOrderMapper salesOrderMapper,
            CustomerMapper customerMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            SalesOrderQueryService salesOrderQueryService,
            WorkflowService workflowService,
            SalesCreditEvaluator salesCreditEvaluator,
            SalesPriceEvaluator salesPriceEvaluator,
            AttachmentService attachmentService
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.customerMapper = customerMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.salesOrderQueryService = salesOrderQueryService;
        this.workflowService = workflowService;
        this.salesCreditEvaluator = salesCreditEvaluator;
        this.salesPriceEvaluator = salesPriceEvaluator;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public SalesOrderResponse submit(Long id, SalesOrderSubmitRequest request) {
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.SALES_ORDER, entity.getId());
        List<SalesOrderLineEntity> existingLines = salesOrderQueryService.selectLines(entity);
        List<SalesOrderLineRequest> lineRequests = existingLines.stream()
                .map(line -> new SalesOrderLineRequest(
                        line.getProductId(),
                        line.getQty(),
                        line.getPrice(),
                        line.getTaxRate(),
                        line.getRemark()
                ))
                .toList();
        salesPriceEvaluator.assertLinesWithinMinPrice(
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getCustomerId(),
                entity.getOrderDate(),
                lineRequests
        );
        CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
        if (customer != null) {
            salesCreditEvaluator.assertWithinCreditLimit(customer, entity, "提交");
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "SUBMITTED", "IN_APPROVAL");
        workflowService.submit(
                BUSINESS_TYPE,
                entity.getId(),
                entity.getOrderNo(),
                "销售订单 " + entity.getOrderNo(),
                request.remark()
        );
        return response;
    }

    @Transactional
    public SalesOrderResponse approve(Long id, SalesOrderApproveRequest request) {
        return approve(id, request, null);
    }

    @Transactional
    public SalesOrderResponse approveWorkflowTask(Long taskId, Long id, SalesOrderApproveRequest request) {
        return approve(id, request, taskId);
    }

    @Transactional
    public SalesOrderResponse reject(Long id, SalesOrderRejectRequest request) {
        return reject(id, request, null);
    }

    @Transactional
    public SalesOrderResponse rejectWorkflowTask(Long taskId, Long id, SalesOrderRejectRequest request) {
        return reject(id, request, taskId);
    }

    @Transactional
    public SalesOrderResponse unapprove(Long id) {
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"APPROVED".equals(entity.getStatus()) || !"APPROVED".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许反审核");
        }
        if (!"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许反审核");
        }
        inventoryPostingService.releaseAllReservations(
                BUSINESS_TYPE,
                entity.getId(),
                auditMetadataFactory.current()
        );
        return transitionWorkflowStatus(entity, "DRAFT", "NOT_SUBMITTED");
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())
                && !"SUBMITTED".equals(entity.getStatus()) && !"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus()) && !"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus())) {
            inventoryPostingService.releaseAllReservations(
                    BUSINESS_TYPE,
                    entity.getId(),
                    auditMetadataFactory.current()
            );
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel(BUSINESS_TYPE, entity.getId(), "作废销售订单");
        return response;
    }

    private SalesOrderResponse approve(
            Long id,
            SalesOrderApproveRequest request,
            Long workflowTaskId
    ) {
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许审批通过");
        }
        CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
        if (customer != null) {
            salesCreditEvaluator.assertWithinCreditLimit(customer, entity, "审批");
        }
        boolean completed;
        if (workflowTaskId == null) {
            completed = workflowService.approve(BUSINESS_TYPE, entity.getId(), request.remark());
        } else {
            completed = workflowService.approveTaskForBusiness(
                    workflowTaskId,
                    BUSINESS_TYPE,
                    entity.getId(),
                    request.remark()
            );
        }
        if (!completed) {
            return salesOrderQueryService.getById(entity.getId());
        }
        reserveOrder(entity);
        return transitionWorkflowStatus(entity, "APPROVED", "APPROVED");
    }

    private SalesOrderResponse reject(
            Long id,
            SalesOrderRejectRequest request,
            Long workflowTaskId
    ) {
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许驳回");
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "REJECTED", "REJECTED");
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

    private void reserveOrder(SalesOrderEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        for (SalesOrderLineEntity line : salesOrderQueryService.selectLines(entity)) {
            inventoryPostingService.reserve(
                    new InventoryReservationCommand(
                            entity.getWarehouseId(),
                            line.getProductId(),
                            BUSINESS_TYPE,
                            entity.getId(),
                            entity.getOrderNo(),
                            line.getId(),
                            line.getQty(),
                            line.getRemark()
                    ),
                    audit,
                    "库存可用量不足，不能审批销售订单"
            );
        }
    }

    private SalesOrderResponse transitionWorkflowStatus(
            SalesOrderEntity entity,
            String status,
            String approvalStatus
    ) {
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                salesOrderMapper.updateById(entity),
                "销售订单已被其他操作修改，请刷新后重试"
        );
        return salesOrderQueryService.getById(entity.getId());
    }
}

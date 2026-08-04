package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sales order posting service - handles approval workflow, inventory reservation and release.
 */
@Service
public class SalesOrderPostingService {

    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final CustomerMapper customerMapper;
    private final InventoryPostingService inventoryPostingService;
    private final SalesOrderQueryService salesOrderQueryService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WorkflowService workflowService;
    private final SalesCreditEvaluator salesCreditEvaluator;

    public SalesOrderPostingService(
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            CustomerMapper customerMapper,
            InventoryPostingService inventoryPostingService,
            SalesOrderQueryService salesOrderQueryService,
            AuditMetadataFactory auditMetadataFactory,
            WorkflowService workflowService,
            SalesCreditEvaluator salesCreditEvaluator
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.customerMapper = customerMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.salesOrderQueryService = salesOrderQueryService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.workflowService = workflowService;
        this.salesCreditEvaluator = salesCreditEvaluator;
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
        SalesOrderEntity entity = requireOrder(id);
        salesOrderQueryService.assertCanView(entity);
        if (!"APPROVED".equals(entity.getStatus()) || !"APPROVED".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许反审核");
        }
        if (!"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许反审核");
        }
        releaseAllReservations(entity.getId());
        return transitionWorkflowStatus(entity, "DRAFT", "NOT_SUBMITTED");
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrderEntity entity = requireOrder(id);
        salesOrderQueryService.assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())
                && !"SUBMITTED".equals(entity.getStatus()) && !"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus()) && !"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus())) {
            releaseAllReservations(entity.getId());
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel("SALES_ORDER", entity.getId(), "作废销售订单");
        return response;
    }

    private SalesOrderResponse approve(Long id, SalesOrderApproveRequest request, Long workflowTaskId) {
        SalesOrderEntity entity = requireOrder(id);
        salesOrderQueryService.assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许审批通过");
        }
        CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
        if (customer != null) {
            salesCreditEvaluator.assertWithinCreditLimit(customer, entity, "审批");
        }
        reserveOrder(entity);
        SalesOrderResponse response = transitionWorkflowStatus(entity, "APPROVED", "APPROVED");
        if (workflowTaskId == null) {
            workflowService.approve("SALES_ORDER", entity.getId(), request.remark());
        } else {
            workflowService.approveTaskForBusiness(workflowTaskId, "SALES_ORDER", entity.getId(), request.remark());
        }
        return response;
    }

    private SalesOrderResponse reject(Long id, SalesOrderRejectRequest request, Long workflowTaskId) {
        SalesOrderEntity entity = requireOrder(id);
        salesOrderQueryService.assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许驳回");
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject("SALES_ORDER", entity.getId(), request.reason());
        } else {
            workflowService.rejectTaskForBusiness(workflowTaskId, "SALES_ORDER", entity.getId(), request.reason());
        }
        return response;
    }

    private void reserveOrder(SalesOrderEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        List<SalesOrderLineEntity> lines = salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId())
                .orderByAsc(SalesOrderLineEntity::getLineNo));
        for (SalesOrderLineEntity line : lines) {
            inventoryPostingService.reserve(
                    new InventoryReservationCommand(
                            entity.getWarehouseId(),
                            line.getProductId(),
                            "SALES_ORDER",
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

    private void releaseAllReservations(Long orderId) {
        inventoryPostingService.releaseAllReservations("SALES_ORDER", orderId, auditMetadataFactory.current());
    }

    private SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity entity = salesOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        return entity;
    }

    private SalesOrderResponse transitionWorkflowStatus(
            SalesOrderEntity entity,
            String status,
            String approvalStatus
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesOrderMapper.updateById(entity), "销售订单已被其他操作修改，请刷新后重试");
        return salesOrderQueryService.getById(entity.getId());
    }
}

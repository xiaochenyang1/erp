package com.tuowei.erp.purchase.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionEntity;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionLineEntity;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionCreateRequest;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionLineRequest;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionResponse;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionUpdateRequest;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Write-side lifecycle, approval and purchase-order conversion for requisitions. */
@Service
public class PurchaseRequisitionCommandService {

    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED");

    private final PurchaseRequisitionMapper requisitionMapper;
    private final PurchaseRequisitionLineMapper lineMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final WorkflowService workflowService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentService attachmentService;
    private final PurchaseRequisitionQueryService queryService;

    public PurchaseRequisitionCommandService(
            PurchaseRequisitionMapper requisitionMapper,
            PurchaseRequisitionLineMapper lineMapper,
            SupplierMapper supplierMapper,
            PurchaseOrderService purchaseOrderService,
            SequenceNumberGenerator sequenceNumberGenerator,
            WorkflowService workflowService,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentService attachmentService,
            PurchaseRequisitionQueryService queryService
    ) {
        this.requisitionMapper = requisitionMapper;
        this.lineMapper = lineMapper;
        this.supplierMapper = supplierMapper;
        this.purchaseOrderService = purchaseOrderService;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.workflowService = workflowService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentService = attachmentService;
        this.queryService = queryService;
    }

    @Transactional
    public PurchaseRequisitionResponse create(PurchaseRequisitionCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validateLines(request == null ? null : request.lines(), audit);
        if (request != null && request.supplierId() != null) {
            requireSupplier(request.supplierId(), audit);
        }
        LocalDateTime now = audit.now();
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setRequisitionNo(sequenceNumberGenerator.nextNumber(
                "PURCHASE_REQUISITION", "采购请购单", request.requisitionDate()
        ));
        entity.setRequisitionDate(request.requisitionDate());
        entity.setNeededDate(request.neededDate());
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("NOT_SUBMITTED");
        entity.setSupplierId(request.supplierId());
        entity.setRequestUserId(audit.userId());
        entity.setRemark(trim(request.remark()));
        entity.setDeletedFlag(0);
        fillCreateAudit(entity, audit, now);
        requisitionMapper.insert(entity);
        saveLines(entity, request.lines(), audit, now);
        return queryService.getById(entity.getId());
    }

    @Transactional
    public PurchaseRequisitionResponse update(Long id, PurchaseRequisitionUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = queryService.requireRequisition(id, audit);
        if (!EDITABLE.contains(entity.getStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许编辑");
        }
        validateLines(request == null ? null : request.lines(), audit);
        if (request != null && request.supplierId() != null) {
            requireSupplier(request.supplierId(), audit);
        }
        LocalDateTime now = audit.now();
        entity.setRequisitionDate(request.requisitionDate());
        entity.setNeededDate(request.neededDate());
        entity.setSupplierId(request.supplierId());
        entity.setRemark(trim(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试"
        );
        lineMapper.delete(new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                .eq(PurchaseRequisitionLineEntity::getCompanyId, audit.companyId())
                .eq(PurchaseRequisitionLineEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseRequisitionLineEntity::getRequisitionId, entity.getId()));
        saveLines(entity, request.lines(), audit, now);
        return queryService.getById(id);
    }

    @Transactional
    public PurchaseRequisitionResponse submit(Long id) {
        PurchaseRequisitionEntity entity = require(id);
        if (!Set.of("DRAFT", "REJECTED").contains(entity.getStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.PURCHASE_REQUISITION, entity.getId());
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "SUBMITTED", "IN_APPROVAL");
        workflowService.submit(
                "PURCHASE_REQUISITION", entity.getId(), entity.getRequisitionNo(),
                "采购请购单 " + entity.getRequisitionNo(), null
        );
        return response;
    }

    @Transactional
    public PurchaseRequisitionResponse approve(Long id) {
        return approve(id, null, null);
    }

    @Transactional
    public PurchaseRequisitionResponse approveWorkflowTask(Long taskId, Long id, String comment) {
        return approve(id, taskId, comment);
    }

    private PurchaseRequisitionResponse approve(Long id, Long workflowTaskId, String comment) {
        PurchaseRequisitionEntity entity = require(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许审批通过");
        }
        boolean completed = workflowTaskId == null
                ? workflowService.approve("PURCHASE_REQUISITION", entity.getId(), comment)
                : workflowService.approveTaskForBusiness(
                        workflowTaskId, "PURCHASE_REQUISITION", entity.getId(), comment
                );
        if (!completed) {
            return queryService.getById(entity.getId());
        }
        return transitionWorkflow(entity, "APPROVED", "APPROVED");
    }

    @Transactional
    public PurchaseRequisitionResponse reject(Long id) {
        return reject(id, null, null);
    }

    @Transactional
    public PurchaseRequisitionResponse rejectWorkflowTask(Long taskId, Long id, String comment) {
        return reject(id, taskId, comment);
    }

    private PurchaseRequisitionResponse reject(Long id, Long workflowTaskId, String comment) {
        PurchaseRequisitionEntity entity = require(id);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许驳回");
        }
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject("PURCHASE_REQUISITION", entity.getId(), comment);
        } else {
            workflowService.rejectTaskForBusiness(
                    workflowTaskId, "PURCHASE_REQUISITION", entity.getId(), comment
            );
        }
        return response;
    }

    @Transactional
    public PurchaseRequisitionResponse cancel(Long id) {
        PurchaseRequisitionEntity entity = require(id);
        if (!Set.of("DRAFT", "SUBMITTED", "REJECTED").contains(entity.getStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许作废");
        }
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel("PURCHASE_REQUISITION", entity.getId(), "作废采购请购单");
        return response;
    }

    @Transactional
    public PurchaseRequisitionResponse convertToPurchaseOrder(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = queryService.requireRequisition(id, audit);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("仅已审批请购单可转采购订单");
        }
        if (entity.getSupplierId() == null) {
            throw new IllegalArgumentException("请先指定供应商后再转采购订单");
        }
        requireSupplier(entity.getSupplierId(), audit);
        List<PurchaseRequisitionLineEntity> lines = queryService.loadLines(entity);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("请购明细不能为空");
        }
        Map<Long, ProductEntity> products = queryService.loadProducts(lines, audit);
        List<PurchaseOrderLineRequest> poLines = new ArrayList<>();
        for (PurchaseRequisitionLineEntity line : lines) {
            ProductEntity product = products.get(line.getProductId());
            BigDecimal price = product == null || product.getPurchasePrice() == null
                    ? BigDecimal.ZERO : product.getPurchasePrice();
            BigDecimal tax = product == null || product.getTaxRate() == null
                    ? BigDecimal.ZERO : product.getTaxRate();
            if (tax.compareTo(BigDecimal.ONE) > 0) {
                tax = tax.divide(new BigDecimal("100"));
            }
            poLines.add(new PurchaseOrderLineRequest(
                    line.getProductId(), line.getQty(), ScalePrecision.amount(price), tax,
                    "请购 " + entity.getRequisitionNo()
            ));
        }
        PurchaseOrderResponse order = purchaseOrderService.create(new PurchaseOrderCreateRequest(
                entity.getSupplierId(), entity.getRequisitionDate(), entity.getNeededDate(),
                "由请购单 " + entity.getRequisitionNo() + " 生成", poLines
        ));
        entity.setStatus("CONVERTED");
        entity.setConvertedOrderId(order.id());
        entity.setConvertedOrderNo(order.orderNo());
        entity.setConvertedTime(audit.now());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试"
        );
        return queryService.getById(id);
    }

    private PurchaseRequisitionEntity require(Long id) {
        return queryService.requireRequisition(id, auditMetadataFactory.current());
    }

    private PurchaseRequisitionResponse transitionWorkflow(
            PurchaseRequisitionEntity entity, String status, String approvalStatus) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试"
        );
        return queryService.getById(entity.getId());
    }

    private void saveLines(
            PurchaseRequisitionEntity entity,
            List<PurchaseRequisitionLineRequest> lines,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        int no = 1;
        for (PurchaseRequisitionLineRequest line : lines) {
            PurchaseRequisitionLineEntity row = new PurchaseRequisitionLineEntity();
            row.setCompanyId(entity.getCompanyId());
            row.setAccountBookId(entity.getAccountBookId());
            row.setRequisitionId(entity.getId());
            row.setLineNo(no++);
            row.setProductId(line.productId());
            row.setQty(ScalePrecision.quantity(line.qty()));
            row.setRemark(trim(line.remark()));
            row.setDeletedFlag(0);
            row.setCreatedBy(audit.userId());
            row.setCreatedTime(now);
            row.setUpdatedBy(audit.userId());
            row.setUpdatedTime(now);
            row.setVersion(0);
            lineMapper.insert(row);
        }
    }

    private void validateLines(List<PurchaseRequisitionLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("请购明细不能为空");
        }
        for (PurchaseRequisitionLineRequest line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("请购明细不能为空");
            }
            queryService.requireProduct(line.productId(), audit);
        }
    }

    private void requireSupplier(Long supplierId, AuditMetadata audit) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || supplier.getDeletedFlag() == null || supplier.getDeletedFlag() != 0
                || !Objects.equals(supplier.getCompanyId(), audit.companyId())
                || !Objects.equals(supplier.getAccountBookId(), audit.accountBookId())
                || !"ACTIVE".equalsIgnoreCase(String.valueOf(supplier.getStatus()))) {
            throw new IllegalArgumentException("供应商不存在或未启用");
        }
    }

    private void fillCreateAudit(PurchaseRequisitionEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

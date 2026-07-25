package com.tuowei.erp.purchase.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
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
import com.tuowei.erp.purchase.requisition.web.*;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchaseRequisitionService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "REJECTED");
    private final PurchaseRequisitionMapper requisitionMapper;
    private final PurchaseRequisitionLineMapper lineMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final WorkflowService workflowService;
    private final AuditMetadataFactory auditMetadataFactory;

    public PurchaseRequisitionService(PurchaseRequisitionMapper requisitionMapper, PurchaseRequisitionLineMapper lineMapper,
                                      ProductMapper productMapper, SupplierMapper supplierMapper,
                                      PurchaseOrderService purchaseOrderService, SequenceNumberGenerator sequenceNumberGenerator,
                                      WorkflowService workflowService,
                                      AuditMetadataFactory auditMetadataFactory) {
        this.requisitionMapper = requisitionMapper; this.lineMapper = lineMapper; this.productMapper = productMapper;
        this.supplierMapper = supplierMapper; this.purchaseOrderService = purchaseOrderService;
        this.sequenceNumberGenerator = sequenceNumberGenerator; this.workflowService = workflowService; this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public PurchaseRequisitionResponse create(PurchaseRequisitionCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validateLines(request.lines(), audit);
        if (request.supplierId() != null) requireSupplier(request.supplierId(), audit);
        LocalDateTime now = audit.now();
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId());
        entity.setRequisitionNo(sequenceNumberGenerator.nextNumber("PURCHASE_REQUISITION", "采购请购单", request.requisitionDate()));
        entity.setRequisitionDate(request.requisitionDate()); entity.setNeededDate(request.neededDate());
        entity.setStatus("DRAFT"); entity.setApprovalStatus("NOT_SUBMITTED"); entity.setSupplierId(request.supplierId()); entity.setRequestUserId(audit.userId());
        entity.setRemark(trim(request.remark())); entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
        requisitionMapper.insert(entity);
        saveLines(entity, request.lines(), audit, now);
        return getById(entity.getId());
    }

    @Transactional
    public PurchaseRequisitionResponse update(Long id, PurchaseRequisitionUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = require(id, audit);
        if (!EDITABLE.contains(entity.getStatus())) throw new IllegalArgumentException("当前请购单状态不允许编辑");
        validateLines(request.lines(), audit);
        if (request.supplierId() != null) requireSupplier(request.supplierId(), audit);
        LocalDateTime now = audit.now();
        entity.setRequisitionDate(request.requisitionDate()); entity.setNeededDate(request.neededDate());
        entity.setSupplierId(request.supplierId()); entity.setRemark(trim(request.remark()));
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试");
        lineMapper.delete(new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                .eq(PurchaseRequisitionLineEntity::getCompanyId, audit.companyId())
                .eq(PurchaseRequisitionLineEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseRequisitionLineEntity::getRequisitionId, entity.getId()));
        saveLines(entity, request.lines(), audit, now);
        return getById(id);
    }

    @Transactional
    public PurchaseRequisitionResponse submit(Long id) {
        PurchaseRequisitionEntity entity = require(id, auditMetadataFactory.current());
        if (!Set.of("DRAFT","REJECTED").contains(entity.getStatus())) throw new IllegalArgumentException("当前请购单状态不允许提交审批");
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "SUBMITTED", "IN_APPROVAL");
        workflowService.submit("PURCHASE_REQUISITION", entity.getId(), entity.getRequisitionNo(), "采购请购单 " + entity.getRequisitionNo(), null);
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
        PurchaseRequisitionEntity entity = require(id, auditMetadataFactory.current());
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许审批通过");
        }
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "APPROVED", "APPROVED");
        if (workflowTaskId == null) workflowService.approve("PURCHASE_REQUISITION", entity.getId(), comment);
        else workflowService.approveTaskForBusiness(workflowTaskId, "PURCHASE_REQUISITION", entity.getId(), comment);
        return response;
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
        PurchaseRequisitionEntity entity = require(id, auditMetadataFactory.current());
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许驳回");
        }
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) workflowService.reject("PURCHASE_REQUISITION", entity.getId(), comment);
        else workflowService.rejectTaskForBusiness(workflowTaskId, "PURCHASE_REQUISITION", entity.getId(), comment);
        return response;
    }

    @Transactional
    public PurchaseRequisitionResponse cancel(Long id) {
        PurchaseRequisitionEntity entity = require(id, auditMetadataFactory.current());
        if (!Set.of("DRAFT","SUBMITTED","REJECTED").contains(entity.getStatus())) {
            throw new IllegalArgumentException("当前请购单状态不允许作废");
        }
        PurchaseRequisitionResponse response = transitionWorkflow(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel("PURCHASE_REQUISITION", entity.getId(), "作废采购请购单");
        return response;
    }

    private PurchaseRequisitionResponse transitionWorkflow(PurchaseRequisitionEntity entity, String status, String approvalStatus) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试");
        return getById(entity.getId());
    }

    @Transactional
    public PurchaseRequisitionResponse convertToPurchaseOrder(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = require(id, audit);
        if (!"APPROVED".equals(entity.getStatus())) throw new IllegalArgumentException("仅已审批请购单可转采购订单");
        if (entity.getSupplierId() == null) throw new IllegalArgumentException("请先指定供应商后再转采购订单");
        requireSupplier(entity.getSupplierId(), audit);
        List<PurchaseRequisitionLineEntity> lines = loadLines(entity);
        if (lines.isEmpty()) throw new IllegalArgumentException("请购明细不能为空");
        Map<Long, ProductEntity> products = loadProducts(lines, audit);
        List<PurchaseOrderLineRequest> poLines = new ArrayList<>();
        for (PurchaseRequisitionLineEntity line : lines) {
            ProductEntity product = products.get(line.getProductId());
            BigDecimal price = product == null || product.getPurchasePrice() == null ? BigDecimal.ZERO : product.getPurchasePrice();
            BigDecimal tax = product == null || product.getTaxRate() == null ? BigDecimal.ZERO : product.getTaxRate();
            if (tax.compareTo(BigDecimal.ONE) > 0) tax = tax.divide(new BigDecimal("100"));
            poLines.add(new PurchaseOrderLineRequest(line.getProductId(), line.getQty(), ScalePrecision.amount(price), tax, "请购 " + entity.getRequisitionNo()));
        }
        PurchaseOrderResponse order = purchaseOrderService.create(new PurchaseOrderCreateRequest(
                entity.getSupplierId(), entity.getRequisitionDate(), entity.getNeededDate(),
                "由请购单 " + entity.getRequisitionNo() + " 生成", poLines
        ));
        entity.setStatus("CONVERTED"); entity.setConvertedOrderId(order.id()); entity.setConvertedOrderNo(order.orderNo());
        entity.setConvertedTime(audit.now()); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试");
        return getById(id);
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = require(id, audit);
        List<PurchaseRequisitionLineEntity> lines = loadLines(entity);
        Map<Long, ProductEntity> products = loadProducts(lines, audit);
        return toResponse(entity, lines, products);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequisitionResponse> list(PurchaseRequisitionPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionPageQuery safe = query == null ? new PurchaseRequisitionPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo()==null?null:safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize()==null?null:safe.getPageSize().intValue());
        LambdaQueryWrapper<PurchaseRequisitionEntity> wrapper = new LambdaQueryWrapper<PurchaseRequisitionEntity>()
                .eq(PurchaseRequisitionEntity::getCompanyId, audit.companyId())
                .eq(PurchaseRequisitionEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseRequisitionEntity::getDeletedFlag, 0)
                .orderByDesc(PurchaseRequisitionEntity::getCreatedTime).orderByDesc(PurchaseRequisitionEntity::getId);
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(PurchaseRequisitionEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(safe.getKeyword())) wrapper.like(PurchaseRequisitionEntity::getRequisitionNo, safe.getKeyword().trim());
        Page<PurchaseRequisitionEntity> page = requisitionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<PurchaseRequisitionResponse> records = page.getRecords().stream().map(e -> {
            List<PurchaseRequisitionLineEntity> lines = loadLines(e);
            return toResponse(e, lines, loadProducts(lines, audit));
        }).toList();
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    private PurchaseRequisitionResponse transition(Long id, Set<String> from, String to) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = require(id, audit);
        if (!from.contains(entity.getStatus())) throw new IllegalArgumentException("当前请购单状态不允许该操作");
        entity.setStatus(to); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(requisitionMapper.updateById(entity), "请购单已被其他操作修改，请刷新后重试");
        return getById(id);
    }

    private void saveLines(PurchaseRequisitionEntity entity, List<PurchaseRequisitionLineRequest> lines, AuditMetadata audit, LocalDateTime now) {
        int no=1;
        for (PurchaseRequisitionLineRequest line : lines) {
            PurchaseRequisitionLineEntity row = new PurchaseRequisitionLineEntity();
            row.setCompanyId(entity.getCompanyId()); row.setAccountBookId(entity.getAccountBookId());
            row.setRequisitionId(entity.getId()); row.setLineNo(no++); row.setProductId(line.productId());
            row.setQty(ScalePrecision.quantity(line.qty())); row.setRemark(trim(line.remark())); row.setDeletedFlag(0);
            row.setCreatedBy(audit.userId()); row.setCreatedTime(now); row.setUpdatedBy(audit.userId()); row.setUpdatedTime(now); row.setVersion(0);
            lineMapper.insert(row);
        }
    }

    private void validateLines(List<PurchaseRequisitionLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("请购明细不能为空");
        for (PurchaseRequisitionLineRequest line : lines) requireProduct(line.productId(), audit);
    }

    private List<PurchaseRequisitionLineEntity> loadLines(PurchaseRequisitionEntity entity) {
        return lineMapper.selectList(new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                .eq(PurchaseRequisitionLineEntity::getCompanyId, entity.getCompanyId())
                .eq(PurchaseRequisitionLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(PurchaseRequisitionLineEntity::getRequisitionId, entity.getId())
                .eq(PurchaseRequisitionLineEntity::getDeletedFlag, 0)
                .orderByAsc(PurchaseRequisitionLineEntity::getLineNo));
    }

    private Map<Long, ProductEntity> loadProducts(List<PurchaseRequisitionLineEntity> lines, AuditMetadata audit) {
        Set<Long> ids = lines.stream().map(PurchaseRequisitionLineEntity::getProductId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(ids).stream().filter(p -> Objects.equals(p.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a,b)->a, HashMap::new));
    }

    private PurchaseRequisitionEntity require(Long id, AuditMetadata audit) {
        PurchaseRequisitionEntity entity = requisitionMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("请购单不存在");
        }
        return entity;
    }

    private ProductEntity requireProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !Objects.equals(product.getCompanyId(), audit.companyId())
                || !Objects.equals(product.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
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

    private PurchaseRequisitionResponse toResponse(PurchaseRequisitionEntity entity, List<PurchaseRequisitionLineEntity> lines, Map<Long, ProductEntity> products) {
        List<PurchaseRequisitionLineResponse> lineResponses = lines.stream().map(line -> {
            ProductEntity p = products.get(line.getProductId());
            return new PurchaseRequisitionLineResponse(line.getId(), line.getLineNo(), line.getProductId(),
                    p==null?null:p.getProductCode(), p==null?null:p.getProductName(), ScalePrecision.quantity(line.getQty()), line.getRemark());
        }).toList();
        return new PurchaseRequisitionResponse(entity.getId(), entity.getRequisitionNo(), entity.getRequisitionDate(), entity.getNeededDate(),
                entity.getStatus(), entity.getApprovalStatus(), entity.getSupplierId(), entity.getConvertedOrderId(), entity.getConvertedOrderNo(), entity.getConvertedTime(),
                entity.getRemark(), lineResponses);
    }

    private String trim(String v){ return !StringUtils.hasText(v)?null:v.trim(); }
}

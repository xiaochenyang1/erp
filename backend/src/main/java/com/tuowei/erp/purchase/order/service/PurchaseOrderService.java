package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ProductAuxUnitConversion;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final ProductValidator productValidator;
    private final PurchaseOrderNumberService purchaseOrderNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final PurchaseOrderTraceService purchaseOrderTraceService;
    private final WorkflowService workflowService;
    private final PurchasePriceEvaluator purchasePriceEvaluator;

    public PurchaseOrderService(
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            SupplierMapper supplierMapper,
            ProductMapper productMapper,
            ProductValidator productValidator,
            PurchaseOrderNumberService purchaseOrderNumberService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderQueryService purchaseOrderQueryService,
            PurchaseOrderTraceService purchaseOrderTraceService,
            WorkflowService workflowService,
            PurchasePriceEvaluator purchasePriceEvaluator
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.supplierMapper = supplierMapper;
        this.productMapper = productMapper;
        this.productValidator = productValidator;
        this.purchaseOrderNumberService = purchaseOrderNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderQueryService = purchaseOrderQueryService;
        this.purchaseOrderTraceService = purchaseOrderTraceService;
        this.workflowService = workflowService;
        this.purchasePriceEvaluator = purchasePriceEvaluator;
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderCreateRequest request) {
        return createInternal(request, null);
    }

    @Transactional
    public PurchaseOrderResponse createFromInquiry(
            PurchaseOrderCreateRequest request,
            PurchaseOrderInquirySource source
    ) {
        requireValidInquirySource(request, source);
        return createInternal(request, source);
    }

    private PurchaseOrderResponse createInternal(
            PurchaseOrderCreateRequest request,
            PurchaseOrderInquirySource source
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity supplier = requireActiveSupplier(request.supplierId(), audit.companyId(), audit.accountBookId());
        purchasePriceEvaluator.assertLinesWithinMaxPrice(
                audit.companyId(),
                audit.accountBookId(),
                supplier.getId(),
                request.orderDate(),
                request.lines()
        );
        OrderTotals totals = calculateTotals(request.lines());
        LocalDateTime now = audit.now();

        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setOrderNo(purchaseOrderNumberService.nextOrderNo(request.orderDate()));
        entity.setSupplierId(supplier.getId());
        entity.setOrderDate(request.orderDate());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("NOT_SUBMITTED");
        entity.setReceiptStatus("NOT_RECEIVED");
        if (source != null) {
            entity.setSourceInquiryId(source.inquiryId());
            entity.setSourceInquiryNo(source.inquiryNo());
            entity.setSourceQuoteId(source.quoteId());
        }
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        purchaseOrderMapper.insert(entity);

        List<PurchaseOrderLineEntity> lines = saveOrderLines(entity.getId(), request.lines(), audit, now, source);

        return toResponse(entity, supplier.getSupplierName(), lines);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long id) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        List<PurchaseOrderLineEntity> lines = loadOrderLines(entity);
        return toResponse(entity, findSupplierName(entity.getSupplierId()), lines);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getBySourceInquiry(Long orderId, Long inquiryId) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseOrderEntity entity = purchaseOrderMapper.selectById(orderId);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(entity.getSourceInquiryId(), inquiryId)) {
            throw new IllegalArgumentException("询价单关联的采购订单不存在");
        }
        return toResponse(entity, findSupplierName(entity.getSupplierId()), loadOrderLines(entity));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderTraceResponse trace(Long id) {
        return purchaseOrderTraceService.trace(getById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(PurchaseOrderPageQuery query) {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        return purchaseOrderQueryService.list(safeQuery);
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderUpdateRequest request) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许编辑");
        }

        PurchaseOrderInquirySource inquirySource = sourceForUpdate(entity, request.lines());
        if (inquirySource != null && !Objects.equals(entity.getSupplierId(), request.supplierId())) {
            throw new IllegalArgumentException("询价单生成的采购订单不允许变更供应商");
        }

        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity supplier = requireActiveSupplier(request.supplierId(), audit.companyId(), audit.accountBookId());
        purchasePriceEvaluator.assertLinesWithinMaxPrice(
                audit.companyId(),
                audit.accountBookId(),
                supplier.getId(),
                request.orderDate(),
                request.lines()
        );
        OrderTotals totals = calculateTotals(request.lines());
        LocalDateTime now = audit.now();

        entity.setSupplierId(supplier.getId());
        entity.setOrderDate(request.orderDate());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(purchaseOrderMapper.updateById(entity), "采购订单已被其他操作修改，请刷新后重试");

        purchaseOrderLineMapper.delete(new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                .eq(PurchaseOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(PurchaseOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(PurchaseOrderLineEntity::getOrderId, entity.getId()));

        saveOrderLines(entity.getId(), request.lines(), audit, now, inquirySource);

        return getById(id);
    }

    @Transactional
    public PurchaseOrderResponse submit(Long id, PurchaseOrderSubmitRequest request) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许提交审批");
        }
        List<PurchaseOrderLineEntity> existingLines = loadOrderLines(entity);
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
        workflowService.submit("PURCHASE_ORDER", entity.getId(), entity.getOrderNo(), "采购订单 " + entity.getOrderNo(), request.remark());
        return response;
    }

    @Transactional
    public PurchaseOrderResponse approve(Long id, PurchaseOrderApproveRequest request) {
        return approve(id, request, null);
    }

    @Transactional
    public PurchaseOrderResponse approveWorkflowTask(Long taskId, Long id, PurchaseOrderApproveRequest request) {
        return approve(id, request, taskId);
    }

    private PurchaseOrderResponse approve(Long id, PurchaseOrderApproveRequest request, Long workflowTaskId) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许审批通过");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "APPROVED", "APPROVED");
        if (workflowTaskId == null) {
            workflowService.approve("PURCHASE_ORDER", entity.getId(), request.remark());
        } else {
            workflowService.approveTaskForBusiness(workflowTaskId, "PURCHASE_ORDER", entity.getId(), request.remark());
        }
        return response;
    }

    @Transactional
    public PurchaseOrderResponse unapprove(Long id) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
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
    public PurchaseOrderResponse rejectWorkflowTask(Long taskId, Long id, PurchaseOrderRejectRequest request) {
        return reject(id, request, taskId);
    }

    private PurchaseOrderResponse reject(Long id, PurchaseOrderRejectRequest request, Long workflowTaskId) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许驳回");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject("PURCHASE_ORDER", entity.getId(), request.reason());
        } else {
            workflowService.rejectTaskForBusiness(workflowTaskId, "PURCHASE_ORDER", entity.getId(), request.reason());
        }
        return response;
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())
                && !"SUBMITTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许作废");
        }
        PurchaseOrderResponse response = transitionWorkflowStatus(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel("PURCHASE_ORDER", entity.getId(), "作废采购订单");
        return response;
    }

    @Transactional
    public PurchaseOrderResponse close(Long id) {
        PurchaseOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购订单状态不允许关闭");
        }
        if ("RECEIVED".equals(entity.getReceiptStatus())) {
            throw new IllegalArgumentException("已完全入库的采购订单不允许关闭");
        }
        return transitionWorkflowStatus(entity, "CLOSED", "APPROVED");
    }

    private PurchaseOrderEntity requireOrder(Long id) {
        PurchaseOrderEntity entity = purchaseOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购订单不存在");
        }
        return entity;
    }

    private SupplierEntity requireActiveSupplier(Long supplierId, Long companyId, Long accountBookId) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || supplier.getDeletedFlag() == null || supplier.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(supplier.getStatus())
                || !Objects.equals(supplier.getCompanyId(), companyId)
                || !Objects.equals(supplier.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("供应商不存在或已停用");
        }
        return supplier;
    }

    private OrderTotals calculateTotals(List<PurchaseOrderLineRequest> lines) {
        PurchaseAmountCalculator.DocumentTotals totals = PurchaseAmountCalculator.DocumentTotals.zero();

        for (PurchaseOrderLineRequest line : lines) {
            totals = totals.add(PurchaseAmountCalculator.line(
                    line.qty(),
                    line.price(),
                    line.taxRate()
            ));
        }

        return new OrderTotals(
                totals.totalQuantity(),
                totals.totalAmount(),
                totals.totalTaxAmount()
        );
    }

    private List<PurchaseOrderLineEntity> saveOrderLines(
            Long orderId,
            List<PurchaseOrderLineRequest> lineRequests,
            AuditMetadata audit,
            LocalDateTime now,
            PurchaseOrderInquirySource source
    ) {
        List<PurchaseOrderLineEntity> lines = new ArrayList<>();
        productValidator.requireProducts(
                lineRequests.stream().map(PurchaseOrderLineRequest::productId).toList(),
                audit.companyId(), audit.accountBookId());
        for (int i = 0; i < lineRequests.size(); i++) {
            PurchaseOrderLineRequest lineRequest = lineRequests.get(i);

            PurchaseOrderLineEntity line = new PurchaseOrderLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setOrderId(orderId);
            line.setLineNo(i + 1);
            line.setProductId(lineRequest.productId());
            ProductAuxUnitConversion.ResolvedAuxUnit aux = ProductAuxUnitConversion.resolve(
                    lineRequest.qty(),
                    lineRequest.auxQty(),
                    lineRequest.auxUnitName(),
                    lineRequest.conversionFactor()
            );
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    aux.stockQty(),
                    lineRequest.price(),
                    lineRequest.taxRate()
            );
            line.setQty(amounts.qty());
            line.setAuxQty(aux.auxQty());
            line.setAuxUnitName(aux.auxUnitName());
            line.setConversionFactor(aux.conversionFactor());
            line.setPrice(amounts.price());
            line.setTaxRate(amounts.taxRate());
            line.setAmount(amounts.amount());
            line.setTaxAmount(amounts.taxAmount());
            if (source != null) {
                line.setSourceInquiryId(source.inquiryId());
                line.setSourceInquiryLineId(source.inquiryLineIds().get(i));
            }
            line.setRemark(lineRequest.remark());
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            lines.add(line);
        }
        // 批量插入优化
        lines.forEach(purchaseOrderLineMapper::insert);
        return lines;
    }

    private void requireValidInquirySource(
            PurchaseOrderCreateRequest request,
            PurchaseOrderInquirySource source
    ) {
        if (source == null
                || source.inquiryId() == null
                || !StringUtils.hasText(source.inquiryNo())
                || source.quoteId() == null
                || source.inquiryLineIds() == null
                || source.inquiryLineIds().size() != request.lines().size()
                || source.inquiryLineIds().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("询价单来源信息不完整");
        }
    }

    private List<PurchaseOrderLineEntity> loadOrderLines(PurchaseOrderEntity entity) {
        return purchaseOrderLineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                .eq(PurchaseOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(PurchaseOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(PurchaseOrderLineEntity::getOrderId, entity.getId())
                .orderByAsc(PurchaseOrderLineEntity::getLineNo));
    }

    private PurchaseOrderInquirySource sourceForUpdate(
            PurchaseOrderEntity entity,
            List<PurchaseOrderLineRequest> requestedLines
    ) {
        if (entity.getSourceInquiryId() == null) {
            return null;
        }
        List<PurchaseOrderLineEntity> existingLines = loadOrderLines(entity);
        if (existingLines.size() != requestedLines.size()) {
            throw new IllegalArgumentException("询价单生成的采购订单不允许增删明细");
        }
        for (int i = 0; i < existingLines.size(); i++) {
            PurchaseOrderLineEntity existing = existingLines.get(i);
            if (!Objects.equals(existing.getProductId(), requestedLines.get(i).productId())
                    || existing.getSourceInquiryLineId() == null) {
                throw new IllegalArgumentException("询价单生成的采购订单不允许变更来源商品明细");
            }
        }
        return new PurchaseOrderInquirySource(
                entity.getSourceInquiryId(),
                entity.getSourceInquiryNo(),
                entity.getSourceQuoteId(),
                existingLines.stream().map(PurchaseOrderLineEntity::getSourceInquiryLineId).toList()
        );
    }

    private void assertCanView(PurchaseOrderEntity entity) {
        purchaseOrderQueryService.assertCanView(entity);
    }

    private void touch(PurchaseOrderEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private PurchaseOrderResponse transitionWorkflowStatus(
            PurchaseOrderEntity entity,
            String status,
            String approvalStatus
    ) {
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        touch(entity);
        OptimisticLockGuard.requireUpdated(purchaseOrderMapper.updateById(entity), "采购订单已被其他操作修改，请刷新后重试");
        return getById(entity.getId());
    }

    private PurchaseOrderResponse toResponse(
            PurchaseOrderEntity entity,
            String supplierName,
            List<PurchaseOrderLineEntity> lines
    ) {
        return new PurchaseOrderResponse(
                entity.getId(),
                entity.getOrderNo(),
                entity.getSupplierId(),
                supplierName,
                entity.getOrderDate(),
                entity.getDeliveryDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getReceiptStatus(),
                entity.getSourceInquiryId(),
                entity.getSourceInquiryNo(),
                entity.getSourceQuoteId(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private String findSupplierName(Long supplierId) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        return supplier == null ? null : supplier.getSupplierName();
    }

    private PurchaseOrderLineResponse toLineResponse(PurchaseOrderLineEntity entity) {
        return new PurchaseOrderLineResponse(
                entity.getId(),
                entity.getLineNo(),
                entity.getProductId(),
                entity.getQty(),
                entity.getAuxQty(),
                entity.getAuxUnitName(),
                entity.getConversionFactor(),
                entity.getPrice(),
                entity.getTaxRate(),
                entity.getAmount(),
                entity.getTaxAmount(),
                entity.getReceivedQty(),
                entity.getSourceInquiryId(),
                entity.getSourceInquiryLineId(),
                entity.getRemark()
        );
    }

    public StreamingResponseBody exportOrders(PurchaseOrderPageQuery query) {
        return purchaseOrderQueryService.exportOrders(query);
    }

    private record OrderTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }

}

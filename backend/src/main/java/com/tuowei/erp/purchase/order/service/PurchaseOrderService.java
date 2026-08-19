package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ProductAuxUnitConversion;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
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
    private final ProductValidator productValidator;
    private final PurchaseOrderNumberService purchaseOrderNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final PurchaseOrderTraceService purchaseOrderTraceService;
    private final PurchaseOrderWorkflowService purchaseOrderWorkflowService;
    private final PurchasePriceEvaluator purchasePriceEvaluator;

    public PurchaseOrderService(
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            SupplierMapper supplierMapper,
            ProductValidator productValidator,
            PurchaseOrderNumberService purchaseOrderNumberService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderQueryService purchaseOrderQueryService,
            PurchaseOrderTraceService purchaseOrderTraceService,
            PurchaseOrderWorkflowService purchaseOrderWorkflowService,
            PurchasePriceEvaluator purchasePriceEvaluator
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.supplierMapper = supplierMapper;
        this.productValidator = productValidator;
        this.purchaseOrderNumberService = purchaseOrderNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderQueryService = purchaseOrderQueryService;
        this.purchaseOrderTraceService = purchaseOrderTraceService;
        this.purchaseOrderWorkflowService = purchaseOrderWorkflowService;
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

        return purchaseOrderQueryService.toResponse(entity, supplier.getSupplierName(), lines);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long id) {
        return purchaseOrderQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getBySourceInquiry(Long orderId, Long inquiryId) {
        return purchaseOrderQueryService.getBySourceInquiry(orderId, inquiryId);
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
        PurchaseOrderEntity entity = purchaseOrderQueryService.requireOrder(id);
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

        return purchaseOrderQueryService.getById(id);
    }

    @Transactional
    public PurchaseOrderResponse submit(Long id, PurchaseOrderSubmitRequest request) {
        return purchaseOrderWorkflowService.submit(id, request);
    }

    @Transactional
    public PurchaseOrderResponse approve(Long id, PurchaseOrderApproveRequest request) {
        return purchaseOrderWorkflowService.approve(id, request);
    }

    @Transactional
    public PurchaseOrderResponse approveWorkflowTask(Long taskId, Long id, PurchaseOrderApproveRequest request) {
        return purchaseOrderWorkflowService.approveWorkflowTask(taskId, id, request);
    }

    @Transactional
    public PurchaseOrderResponse unapprove(Long id) {
        return purchaseOrderWorkflowService.unapprove(id);
    }

    @Transactional
    public PurchaseOrderResponse reject(Long id, PurchaseOrderRejectRequest request) {
        return purchaseOrderWorkflowService.reject(id, request);
    }

    @Transactional
    public PurchaseOrderResponse rejectWorkflowTask(Long taskId, Long id, PurchaseOrderRejectRequest request) {
        return purchaseOrderWorkflowService.rejectWorkflowTask(taskId, id, request);
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        return purchaseOrderWorkflowService.cancel(id);
    }

    @Transactional
    public PurchaseOrderResponse close(Long id) {
        return purchaseOrderWorkflowService.close(id);
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

    private PurchaseOrderInquirySource sourceForUpdate(
            PurchaseOrderEntity entity,
            List<PurchaseOrderLineRequest> requestedLines
    ) {
        if (entity.getSourceInquiryId() == null) {
            return null;
        }
        List<PurchaseOrderLineEntity> existingLines = purchaseOrderQueryService.selectLines(entity);
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

    public StreamingResponseBody exportOrders(PurchaseOrderPageQuery query) {
        return purchaseOrderQueryService.exportOrders(query);
    }

    private record OrderTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }

}

package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
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
import com.tuowei.erp.purchase.order.web.PurchaseOrderDocumentSummary;
import com.tuowei.erp.purchase.order.web.PurchaseOrderExecutionInfo;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRelatedDocs;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final ProductValidator productValidator;
    private final PurchaseOrderNumberService purchaseOrderNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PayableMapper payableMapper;
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final PaymentMapper paymentMapper;
    private final VoucherMapper voucherMapper;
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
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            PayableMapper payableMapper,
            PaymentAllocationMapper paymentAllocationMapper,
            PaymentMapper paymentMapper,
            VoucherMapper voucherMapper,
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
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.payableMapper = payableMapper;
        this.paymentAllocationMapper = paymentAllocationMapper;
        this.paymentMapper = paymentMapper;
        this.voucherMapper = voucherMapper;
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
        PurchaseOrderResponse order = getById(id);
        List<PurchaseReceiptEntity> receipts = purchaseReceiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getDeletedFlag, 0)
                .eq(PurchaseReceiptEntity::getOrderId, id)
                .orderByDesc(PurchaseReceiptEntity::getReceiptDate)
                .orderByDesc(PurchaseReceiptEntity::getId));
        List<Long> receiptIds = receipts.stream().map(PurchaseReceiptEntity::getId).toList();
        List<PurchaseReturnEntity> returns = receiptIds.isEmpty()
                ? List.of()
                : purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getDeletedFlag, 0)
                .in(PurchaseReturnEntity::getReceiptId, receiptIds)
                .orderByDesc(PurchaseReturnEntity::getReturnDate)
                .orderByDesc(PurchaseReturnEntity::getId));

        List<PayableEntity> payables = loadTracePayables(receipts, returns);
        List<PaymentEntity> payments = loadTracePayments(payables);
        List<VoucherEntity> vouchers = loadTraceVouchers(receipts, returns, payments);

        return new PurchaseOrderTraceResponse(
                order,
                workflowService.approvalInfo("PURCHASE_ORDER", id),
                executionInfo(order),
                new PurchaseOrderRelatedDocs(
                        receipts.stream().map(this::receiptSummary).toList(),
                        returns.stream().map(this::returnSummary).toList(),
                        payables.stream().map(this::payableSummary).toList(),
                        payments.stream().map(this::paymentSummary).toList(),
                        vouchers.stream().map(this::voucherSummary).toList()
                )
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(PurchaseOrderPageQuery query) {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        String approvalStatus = normalizeStatus(safeQuery.getApprovalStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        Page<PurchaseOrderEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = buildListQuery(keyword, status, approvalStatus, safeQuery.getSupplierId());
        wrapper = dataScopeService.applyPurchaseOrderScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<PurchaseOrderEntity> result = purchaseOrderMapper.selectPage(
                page,
                wrapper
        );
        Map<Long, String> supplierNames = loadSupplierNames(result.getRecords());

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toSummaryResponse(entity, supplierNames.get(entity.getSupplierId())))
                        .toList()
        );
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
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    lineRequest.qty(),
                    lineRequest.price(),
                    lineRequest.taxRate()
            );
            line.setQty(amounts.qty());
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

    private LambdaQueryWrapper<PurchaseOrderEntity> buildListQuery(
            String keyword,
            String status,
            String approvalStatus,
            Long supplierId
    ) {
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseOrderEntity::getOrderNo, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseOrderEntity::getStatus, status);
        }
        if (StringUtils.hasText(approvalStatus)) {
            wrapper.eq(PurchaseOrderEntity::getApprovalStatus, approvalStatus);
        }
        if (supplierId != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, supplierId);
        }
        return wrapper.orderByDesc(PurchaseOrderEntity::getId);
    }

    private void assertCanView(PurchaseOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
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

    private PurchaseOrderExecutionInfo executionInfo(PurchaseOrderResponse order) {
        BigDecimal orderedQty = ScalePrecision.zeroDefault(order.totalQuantity());
        BigDecimal receivedQty = order.lines().stream()
                .map(PurchaseOrderLineResponse::receivedQty)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOrderExecutionInfo(
                ScalePrecision.quantity(orderedQty),
                ScalePrecision.quantity(receivedQty),
                ScalePrecision.quantity(orderedQty.subtract(receivedQty)),
                order.receiptStatus()
        );
    }

    private List<PayableEntity> loadTracePayables(List<PurchaseReceiptEntity> receipts, List<PurchaseReturnEntity> returns) {
        List<PayableEntity> result = new ArrayList<>();
        List<Long> receiptIds = receipts.stream().map(PurchaseReceiptEntity::getId).toList();
        if (!receiptIds.isEmpty()) {
            result.addAll(payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getDeletedFlag, 0)
                    .eq(PayableEntity::getSourceType, "PURCHASE_RECEIPT")
                    .in(PayableEntity::getSourceId, receiptIds)));
        }
        List<Long> returnIds = returns.stream().map(PurchaseReturnEntity::getId).toList();
        if (!returnIds.isEmpty()) {
            result.addAll(payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                    .eq(PayableEntity::getDeletedFlag, 0)
                    .eq(PayableEntity::getSourceType, "PURCHASE_RETURN")
                    .in(PayableEntity::getSourceId, returnIds)));
        }
        return result;
    }

    private List<PaymentEntity> loadTracePayments(List<PayableEntity> payables) {
        List<Long> payableIds = payables.stream().map(PayableEntity::getId).toList();
        if (payableIds.isEmpty()) {
            return List.of();
        }
        List<Long> paymentIds = paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
                        .in(PaymentAllocationEntity::getPayableId, payableIds))
                .stream()
                .map(PaymentAllocationEntity::getPaymentId)
                .distinct()
                .toList();
        if (paymentIds.isEmpty()) {
            return List.of();
        }
        return paymentMapper.selectList(new LambdaQueryWrapper<PaymentEntity>()
                .eq(PaymentEntity::getDeletedFlag, 0)
                .in(PaymentEntity::getId, paymentIds)
                .orderByDesc(PaymentEntity::getPaymentDate)
                .orderByDesc(PaymentEntity::getId));
    }

    private List<VoucherEntity> loadTraceVouchers(
            List<PurchaseReceiptEntity> receipts,
            List<PurchaseReturnEntity> returns,
            List<PaymentEntity> payments
    ) {
        List<VoucherEntity> result = new ArrayList<>();
        addVouchers(result, "PURCHASE_RECEIPT", receipts.stream().map(PurchaseReceiptEntity::getId).toList());
        addVouchers(result, "PURCHASE_RETURN", returns.stream().map(PurchaseReturnEntity::getId).toList());
        addVouchers(result, "PAYMENT", payments.stream().map(PaymentEntity::getId).toList());
        return result;
    }

    private void addVouchers(List<VoucherEntity> result, String sourceType, List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return;
        }
        result.addAll(voucherMapper.selectList(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getDeletedFlag, 0)
                .eq(VoucherEntity::getSourceType, sourceType)
                .in(VoucherEntity::getSourceId, sourceIds)
                .orderByDesc(VoucherEntity::getBizDate)
                .orderByDesc(VoucherEntity::getId)));
    }

    private PurchaseOrderDocumentSummary receiptSummary(PurchaseReceiptEntity receipt) {
        return new PurchaseOrderDocumentSummary(
                receipt.getId(),
                receipt.getReceiptNo(),
                "PURCHASE_RECEIPT",
                receipt.getReceiptDate(),
                receipt.getStatus(),
                documentAmount(receipt.getTotalAmount(), receipt.getTotalTaxAmount())
        );
    }

    private PurchaseOrderDocumentSummary returnSummary(PurchaseReturnEntity purchaseReturn) {
        return new PurchaseOrderDocumentSummary(
                purchaseReturn.getId(),
                purchaseReturn.getReturnNo(),
                "PURCHASE_RETURN",
                purchaseReturn.getReturnDate(),
                purchaseReturn.getStatus(),
                documentAmount(purchaseReturn.getTotalAmount(), purchaseReturn.getTotalTaxAmount())
        );
    }

    private PurchaseOrderDocumentSummary payableSummary(PayableEntity payable) {
        return new PurchaseOrderDocumentSummary(
                payable.getId(),
                payable.getPayableNo(),
                payable.getSourceType(),
                payable.getBizDate(),
                payable.getStatus(),
                payable.getOriginalAmount()
        );
    }

    private PurchaseOrderDocumentSummary paymentSummary(PaymentEntity payment) {
        return new PurchaseOrderDocumentSummary(
                payment.getId(),
                payment.getPaymentNo(),
                "PAYMENT",
                payment.getPaymentDate(),
                payment.getStatus(),
                payment.getAmount()
        );
    }

    private PurchaseOrderDocumentSummary voucherSummary(VoucherEntity voucher) {
        return new PurchaseOrderDocumentSummary(
                voucher.getId(),
                voucher.getVoucherNo(),
                voucher.getSourceType(),
                voucher.getBizDate(),
                voucher.getStatus(),
                voucher.getAmount()
        );
    }

    private BigDecimal documentAmount(BigDecimal totalAmount, BigDecimal totalTaxAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(totalAmount).add(ScalePrecision.zeroDefault(totalTaxAmount)));
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

    private PurchaseOrderResponse toSummaryResponse(PurchaseOrderEntity entity, String supplierName) {
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
                List.of()
        );
    }

    private String findSupplierName(Long supplierId) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        return supplier == null ? null : supplier.getSupplierName();
    }

    private Map<Long, String> loadSupplierNames(List<PurchaseOrderEntity> orders) {
        List<Long> supplierIds = orders.stream()
                .map(PurchaseOrderEntity::getSupplierId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(SupplierEntity::getId, SupplierEntity::getSupplierName));
    }

    private PurchaseOrderLineResponse toLineResponse(PurchaseOrderLineEntity entity) {
        return new PurchaseOrderLineResponse(
                entity.getId(),
                entity.getLineNo(),
                entity.getProductId(),
                entity.getQty(),
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

    private void exportToCsv(PurchaseOrderPageQuery query, OutputStream outputStream) throws IOException {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();

        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = buildListQuery(
                safeQuery.getKeyword(),
                safeQuery.getStatus(),
                safeQuery.getApprovalStatus(),
                safeQuery.getSupplierId()
        );
        wrapper.eq(PurchaseOrderEntity::getCompanyId, audit.companyId())
                .eq(PurchaseOrderEntity::getAccountBookId, audit.accountBookId());
        wrapper.orderByDesc(PurchaseOrderEntity::getOrderDate).orderByDesc(PurchaseOrderEntity::getId);

        List<String> headers = List.of(
                "订单编号", "供应商", "订单日期", "交货日期",
                "订单金额", "状态", "创建人", "创建时间", "备注"
        );

        List<PurchaseOrderEntity> orders = purchaseOrderMapper.selectList(wrapper);
        Map<Long, String> supplierNames = loadSupplierNames(orders);
        Map<Long, String> userNames = loadUserNames(orders);

        List<List<String>> rows = orders.stream()
                .map(order -> List.of(
                        order.getOrderNo() != null ? order.getOrderNo() : "",
                        supplierNames.getOrDefault(order.getSupplierId(), ""),
                        order.getOrderDate() != null ? order.getOrderDate().toString() : "",
                        order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : "",
                        order.getTotalAmount() != null ? order.getTotalAmount().toString() : "",
                        order.getStatus() != null ? order.getStatus() : "",
                        userNames.getOrDefault(order.getCreatedBy(), ""),
                        order.getCreatedTime() != null ? order.getCreatedTime().toString() : "",
                        order.getRemark() != null ? order.getRemark() : ""
                ))
                .toList();

        CsvExport.write(outputStream, headers, rows);
    }

    public StreamingResponseBody exportOrders(PurchaseOrderPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return outputStream -> withAuthentication(authentication, () -> exportToCsv(query, outputStream));
    }

    private Map<Long, String> loadUserNames(List<PurchaseOrderEntity> orders) {
        Set<Long> userIds = orders.stream()
                .map(PurchaseOrderEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
    }

    private record OrderTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}

package com.tuowei.erp.purchase.inquiry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService.QuoteLinePrice;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryUpdateRequest;
import com.tuowei.erp.purchase.order.service.PurchaseOrderInquirySource;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Write-side creation, lifecycle commands, quote selection and atomic PO conversion. */
@Service
public class PurchaseInquiryCommandService {
    private static final String DRAFT = "DRAFT";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String CLOSED = "CLOSED";
    private static final String CONVERTED = "CONVERTED";
    private static final String CANCELLED = "CANCELLED";

    private final PurchaseInquiryMapper inquiryMapper;
    private final PurchaseInquiryLineMapper lineMapper;
    private final PurchaseInquiryNumberService numberService;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseInquiryQuoteService quoteService;
    private final PurchaseInquiryQueryService queryService;

    public PurchaseInquiryCommandService(
            PurchaseInquiryMapper inquiryMapper,
            PurchaseInquiryLineMapper lineMapper,
            PurchaseInquiryNumberService numberService,
            ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderService purchaseOrderService,
            PurchaseInquiryQuoteService quoteService,
            PurchaseInquiryQueryService queryService
    ) {
        this.inquiryMapper = inquiryMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderService = purchaseOrderService;
        this.quoteService = quoteService;
        this.queryService = queryService;
    }

    @Transactional
    public PurchaseInquiryResponse create(PurchaseInquiryCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validateLines(request.lines(), audit);
        LocalDateTime now = audit.now();
        PurchaseInquiryEntity inquiry = new PurchaseInquiryEntity();
        inquiry.setCompanyId(audit.companyId()); inquiry.setAccountBookId(audit.accountBookId());
        inquiry.setInquiryNo(numberService.nextInquiryNo(request.inquiryDate())); inquiry.setInquiryDate(request.inquiryDate());
        inquiry.setStatus(DRAFT); inquiry.setTitle(trim(request.title())); inquiry.setDeletedFlag(0); inquiry.setRemark(trim(request.remark()));
        inquiry.setCreatedBy(audit.userId()); inquiry.setCreatedTime(now); inquiry.setUpdatedBy(audit.userId()); inquiry.setUpdatedTime(now); inquiry.setVersion(0);
        inquiryMapper.insert(inquiry);
        List<PurchaseInquiryLineEntity> lines = insertLines(inquiry, request.lines(), audit, now);
        return queryService.toResponse(inquiry, lines, List.of());
    }

    @Transactional
    public PurchaseInquiryResponse update(Long id, PurchaseInquiryUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = queryService.requireInquiry(id, audit);
        if (!DRAFT.equals(inquiry.getStatus())) throw new IllegalArgumentException("当前询价单状态不允许编辑");
        validateLines(request.lines(), audit);
        LocalDateTime now = audit.now();
        softDeleteLines(inquiry, audit, now);
        inquiry.setInquiryDate(request.inquiryDate()); inquiry.setTitle(trim(request.title())); inquiry.setRemark(trim(request.remark()));
        inquiry.setUpdatedBy(audit.userId()); inquiry.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        List<PurchaseInquiryLineEntity> lines = insertLines(inquiry, request.lines(), audit, now);
        return queryService.toResponse(inquiry, lines, quoteService.loadQuoteResponses(inquiry));
    }

    @Transactional
    public PurchaseInquiryResponse submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchaseInquiryEntity inquiry = queryService.requireInquiry(id, audit);
        if (!DRAFT.equals(inquiry.getStatus())) throw new IllegalArgumentException("当前询价单状态不允许提交");
        if (queryService.loadLines(inquiry).isEmpty()) throw new IllegalArgumentException("询价单没有明细，无法提交");
        inquiry.setStatus(SUBMITTED); touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        return queryService.getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse addQuote(Long id, PurchaseInquiryQuoteRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchaseInquiryEntity inquiry = queryService.requireInquiry(id, audit);
        if (!SUBMITTED.equals(inquiry.getStatus())) throw new IllegalArgumentException("仅已提交的询价单可录入报价");
        quoteService.addQuote(inquiry, request, audit); touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        return queryService.getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse selectQuote(Long id, PurchaseInquirySelectQuoteRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchaseInquiryEntity inquiry = queryService.requireInquiry(id, audit);
        if (!SUBMITTED.equals(inquiry.getStatus())) throw new IllegalArgumentException("仅已提交的询价单可选定中标报价");
        PurchaseInquiryQuoteEntity quote = quoteService.selectWinningQuote(inquiry, request.quoteId(), audit);
        inquiry.setSelectedSupplierId(quote.getSupplierId()); inquiry.setSelectedQuoteId(quote.getId()); inquiry.setStatus(CLOSED); touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        return queryService.getById(id);
    }

    @Transactional
    public PurchaseOrderResponse convertToPurchaseOrder(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = inquiryMapper.selectForUpdate(id, audit.companyId(), audit.accountBookId());
        if (inquiry == null) throw new IllegalArgumentException("询价单不存在");
        if (inquiry.getConvertedOrderId() != null) return purchaseOrderService.getBySourceInquiry(inquiry.getConvertedOrderId(), inquiry.getId());
        if (!CLOSED.equals(inquiry.getStatus()) || inquiry.getSelectedSupplierId() == null || inquiry.getSelectedQuoteId() == null) {
            throw new IllegalArgumentException("仅已选定中标报价的询价单可转换为采购订单");
        }
        PurchaseInquiryQuoteEntity quote = quoteService.requireSelectedQuote(inquiry, audit);
        List<PurchaseInquiryLineEntity> lines = queryService.loadLines(inquiry);
        if (lines.isEmpty()) throw new IllegalArgumentException("询价单没有明细，无法转换为采购订单");
        if (lines.stream().anyMatch(line -> line.getId() == null)) throw new IllegalArgumentException("询价单明细来源信息不完整");
        Map<Long, QuoteLinePrice> prices = quoteService.resolveQuotePrices(inquiry, quote, lines,
                "中标报价缺少单价，无法转换为采购订单", "中标报价明细不完整，无法转换为采购订单");
        List<PurchaseOrderLineRequest> orderLines = lines.stream().map(line -> {
            QuoteLinePrice price = prices.get(line.getId());
            return new PurchaseOrderLineRequest(line.getProductId(), ScalePrecision.quantity(line.getQty()), price.unitPrice(), price.taxRate(), line.getRemark());
        }).toList();
        String remark = "来源询价单 " + inquiry.getInquiryNo() + (StringUtils.hasText(inquiry.getRemark()) ? "；" + inquiry.getRemark() : "");
        PurchaseOrderResponse order = purchaseOrderService.createFromInquiry(
                new PurchaseOrderCreateRequest(quote.getSupplierId(), inquiry.getInquiryDate(), null, remark, orderLines),
                new PurchaseOrderInquirySource(inquiry.getId(), inquiry.getInquiryNo(), quote.getId(), lines.stream().map(PurchaseInquiryLineEntity::getId).toList()));
        inquiry.setStatus(CONVERTED); inquiry.setConvertedOrderId(order.id()); inquiry.setConvertedOrderNo(order.orderNo());
        inquiry.setConvertedBy(audit.userId()); inquiry.setConvertedTime(audit.now()); touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        return order;
    }

    @Transactional
    public PurchaseInquiryResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchaseInquiryEntity inquiry = queryService.requireInquiry(id, audit);
        if (!DRAFT.equals(inquiry.getStatus()) && !SUBMITTED.equals(inquiry.getStatus())) throw new IllegalArgumentException("当前询价单状态不允许作废");
        inquiry.setStatus(CANCELLED); touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(inquiryMapper.updateById(inquiry), "询价单已被其他操作修改，请刷新后重试");
        return queryService.getById(id);
    }

    private void validateLines(List<PurchaseInquiryLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("lines不能为空");
        for (PurchaseInquiryLineRequest line : lines) {
            productValidator.requireProduct(line.productId(), audit.companyId(), audit.accountBookId());
            if (line.qty() == null || line.qty().signum() <= 0) throw new IllegalArgumentException("qty必须大于0");
        }
    }

    private List<PurchaseInquiryLineEntity> insertLines(PurchaseInquiryEntity inquiry, List<PurchaseInquiryLineRequest> requests, AuditMetadata audit, LocalDateTime now) {
        List<PurchaseInquiryLineEntity> lines = new ArrayList<>(); int no = 1;
        for (PurchaseInquiryLineRequest request : requests) {
            PurchaseInquiryLineEntity line = new PurchaseInquiryLineEntity();
            line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId()); line.setInquiryId(inquiry.getId()); line.setLineNo(no++);
            line.setProductId(request.productId()); line.setQty(ScalePrecision.quantity(request.qty())); line.setDeletedFlag(0); line.setRemark(trim(request.remark()));
            line.setCreatedBy(audit.userId()); line.setCreatedTime(now); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); line.setVersion(0);
            lineMapper.insert(line); lines.add(line);
        }
        return lines;
    }

    private void softDeleteLines(PurchaseInquiryEntity inquiry, AuditMetadata audit, LocalDateTime now) {
        for (PurchaseInquiryLineEntity line : queryService.loadLines(inquiry)) {
            line.setDeletedFlag(1); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(lineMapper.updateById(line), "询价单明细已被其他操作修改，请刷新后重试");
        }
    }

    private void touch(PurchaseInquiryEntity inquiry, AuditMetadata audit) { inquiry.setUpdatedBy(audit.userId()); inquiry.setUpdatedTime(audit.now()); }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}

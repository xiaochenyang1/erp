package com.tuowei.erp.purchase.inquiry.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService.QuoteLinePrice;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
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

@Service
public class PurchaseInquiryService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final PurchaseInquiryMapper purchaseInquiryMapper;
    private final PurchaseInquiryLineMapper purchaseInquiryLineMapper;
    private final PurchaseInquiryNumberService purchaseInquiryNumberService;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseInquiryQuoteService purchaseInquiryQuoteService;
    private final PurchaseInquiryQueryService purchaseInquiryQueryService;

    public PurchaseInquiryService(
            PurchaseInquiryMapper purchaseInquiryMapper,
            PurchaseInquiryLineMapper purchaseInquiryLineMapper,
            PurchaseInquiryNumberService purchaseInquiryNumberService,
            ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderService purchaseOrderService,
            PurchaseInquiryQuoteService purchaseInquiryQuoteService,
            PurchaseInquiryQueryService purchaseInquiryQueryService
    ) {
        this.purchaseInquiryMapper = purchaseInquiryMapper;
        this.purchaseInquiryLineMapper = purchaseInquiryLineMapper;
        this.purchaseInquiryNumberService = purchaseInquiryNumberService;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseInquiryQuoteService = purchaseInquiryQuoteService;
        this.purchaseInquiryQueryService = purchaseInquiryQueryService;
    }

    @Transactional
    public PurchaseInquiryResponse create(PurchaseInquiryCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validateLines(request.lines(), audit);
        LocalDateTime now = audit.now();

        PurchaseInquiryEntity inquiry = new PurchaseInquiryEntity();
        inquiry.setCompanyId(audit.companyId());
        inquiry.setAccountBookId(audit.accountBookId());
        inquiry.setInquiryNo(purchaseInquiryNumberService.nextInquiryNo(request.inquiryDate()));
        inquiry.setInquiryDate(request.inquiryDate());
        inquiry.setStatus(STATUS_DRAFT);
        inquiry.setSelectedSupplierId(null);
        inquiry.setSelectedQuoteId(null);
        inquiry.setTitle(trimToNull(request.title()));
        inquiry.setDeletedFlag(0);
        inquiry.setRemark(trimToNull(request.remark()));
        inquiry.setCreatedBy(audit.userId());
        inquiry.setCreatedTime(now);
        inquiry.setUpdatedBy(audit.userId());
        inquiry.setUpdatedTime(now);
        inquiry.setVersion(0);
        purchaseInquiryMapper.insert(inquiry);

        List<PurchaseInquiryLineEntity> lines = insertLines(inquiry, request.lines(), audit, now);
        return purchaseInquiryQueryService.toResponse(inquiry, lines, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseInquiryResponse> list(PurchaseInquiryPageQuery query) {
        PurchaseInquiryPageQuery safeQuery = query == null ? new PurchaseInquiryPageQuery() : query;
        return purchaseInquiryQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryResponse getById(Long id) {
        return purchaseInquiryQueryService.getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse update(Long id, PurchaseInquiryUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = purchaseInquiryQueryService.requireInquiry(id, audit);
        if (!STATUS_DRAFT.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("当前询价单状态不允许编辑");
        }
        validateLines(request.lines(), audit);
        LocalDateTime now = audit.now();

        softDeleteLines(inquiry, audit, now);

        inquiry.setInquiryDate(request.inquiryDate());
        inquiry.setTitle(trimToNull(request.title()));
        inquiry.setRemark(trimToNull(request.remark()));
        inquiry.setUpdatedBy(audit.userId());
        inquiry.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );

        List<PurchaseInquiryLineEntity> lines = insertLines(inquiry, request.lines(), audit, now);
        return purchaseInquiryQueryService.toResponse(
                inquiry,
                lines,
                purchaseInquiryQuoteService.loadQuoteResponses(inquiry)
        );
    }

    @Transactional
    public PurchaseInquiryResponse submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = purchaseInquiryQueryService.requireInquiry(id, audit);
        if (!STATUS_DRAFT.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("当前询价单状态不允许提交");
        }
        if (purchaseInquiryQueryService.loadLines(inquiry).isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法提交");
        }
        inquiry.setStatus(STATUS_SUBMITTED);
        touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse addQuote(Long id, PurchaseInquiryQuoteRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = purchaseInquiryQueryService.requireInquiry(id, audit);
        if (!STATUS_SUBMITTED.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("仅已提交的询价单可录入报价");
        }
        purchaseInquiryQuoteService.addQuote(inquiry, request, audit);

        touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public PurchaseInquiryResponse selectQuote(Long id, PurchaseInquirySelectQuoteRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = purchaseInquiryQueryService.requireInquiry(id, audit);
        if (!STATUS_SUBMITTED.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("仅已提交的询价单可选定中标报价");
        }
        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteService.selectWinningQuote(
                inquiry,
                request.quoteId(),
                audit
        );
        LocalDateTime now = audit.now();

        inquiry.setSelectedSupplierId(quote.getSupplierId());
        inquiry.setSelectedQuoteId(quote.getId());
        inquiry.setStatus(STATUS_CLOSED);
        inquiry.setUpdatedBy(audit.userId());
        inquiry.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryPoPrefillResponse poPrefill(Long id) {
        return purchaseInquiryQueryService.poPrefill(id);
    }

    @Transactional
    public PurchaseOrderResponse convertToPurchaseOrder(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiryForUpdate(id, audit);
        if (inquiry.getConvertedOrderId() != null) {
            return purchaseOrderService.getBySourceInquiry(inquiry.getConvertedOrderId(), inquiry.getId());
        }
        if (!STATUS_CLOSED.equals(inquiry.getStatus())
                || inquiry.getSelectedSupplierId() == null
                || inquiry.getSelectedQuoteId() == null) {
            throw new IllegalArgumentException("仅已选定中标报价的询价单可转换为采购订单");
        }

        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteService.requireSelectedQuote(inquiry, audit);

        List<PurchaseInquiryLineEntity> inquiryLines = purchaseInquiryQueryService.loadLines(inquiry);
        if (inquiryLines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法转换为采购订单");
        }
        if (inquiryLines.stream().anyMatch(line -> line.getId() == null)) {
            throw new IllegalArgumentException("询价单明细来源信息不完整");
        }

        Map<Long, QuoteLinePrice> quotePrices = purchaseInquiryQuoteService.resolveQuotePrices(
                inquiry,
                quote,
                inquiryLines,
                "中标报价缺少单价，无法转换为采购订单",
                "中标报价明细不完整，无法转换为采购订单"
        );
        List<PurchaseOrderLineRequest> orderLines = inquiryLines.stream()
                .map(line -> {
                    QuoteLinePrice linePrice = quotePrices.get(line.getId());
                    return new PurchaseOrderLineRequest(
                            line.getProductId(),
                            ScalePrecision.quantity(line.getQty()),
                            linePrice.unitPrice(),
                            linePrice.taxRate(),
                            line.getRemark()
                    );
                })
                .toList();
        String remark = "来源询价单 " + inquiry.getInquiryNo();
        if (StringUtils.hasText(inquiry.getRemark())) {
            remark = remark + "；" + inquiry.getRemark();
        }

        PurchaseOrderResponse order = purchaseOrderService.createFromInquiry(
                new PurchaseOrderCreateRequest(
                        quote.getSupplierId(),
                        inquiry.getInquiryDate(),
                        null,
                        remark,
                        orderLines
                ),
                new PurchaseOrderInquirySource(
                        inquiry.getId(),
                        inquiry.getInquiryNo(),
                        quote.getId(),
                        inquiryLines.stream().map(PurchaseInquiryLineEntity::getId).toList()
                )
        );

        inquiry.setStatus(STATUS_CONVERTED);
        inquiry.setConvertedOrderId(order.id());
        inquiry.setConvertedOrderNo(order.orderNo());
        inquiry.setConvertedBy(audit.userId());
        inquiry.setConvertedTime(audit.now());
        touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );
        return order;
    }

    @Transactional
    public PurchaseInquiryResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = purchaseInquiryQueryService.requireInquiry(id, audit);
        if (!STATUS_DRAFT.equals(inquiry.getStatus()) && !STATUS_SUBMITTED.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("当前询价单状态不允许作废");
        }
        inquiry.setStatus(STATUS_CANCELLED);
        touch(inquiry, audit);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryMapper.updateById(inquiry),
                "询价单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    private void validateLines(List<PurchaseInquiryLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines不能为空");
        }
        for (PurchaseInquiryLineRequest line : lines) {
            productValidator.requireProduct(line.productId(), audit.companyId(), audit.accountBookId());
            if (line.qty() == null || line.qty().signum() <= 0) {
                throw new IllegalArgumentException("qty必须大于0");
            }
        }
    }

    private List<PurchaseInquiryLineEntity> insertLines(
            PurchaseInquiryEntity inquiry,
            List<PurchaseInquiryLineRequest> lineRequests,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<PurchaseInquiryLineEntity> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseInquiryLineRequest request : lineRequests) {
            PurchaseInquiryLineEntity line = new PurchaseInquiryLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setInquiryId(inquiry.getId());
            line.setLineNo(lineNo++);
            line.setProductId(request.productId());
            line.setQty(ScalePrecision.quantity(request.qty()));
            line.setDeletedFlag(0);
            line.setRemark(trimToNull(request.remark()));
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            purchaseInquiryLineMapper.insert(line);
            lines.add(line);
        }
        return lines;
    }

    private void softDeleteLines(PurchaseInquiryEntity inquiry, AuditMetadata audit, LocalDateTime now) {
        List<PurchaseInquiryLineEntity> existing = purchaseInquiryQueryService.loadLines(inquiry);
        for (PurchaseInquiryLineEntity line : existing) {
            line.setDeletedFlag(1);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseInquiryLineMapper.updateById(line),
                    "询价单明细已被其他操作修改，请刷新后重试"
            );
        }
    }

    private PurchaseInquiryEntity requireInquiryForUpdate(Long id, AuditMetadata audit) {
        PurchaseInquiryEntity entity = purchaseInquiryMapper.selectForUpdate(
                id,
                audit.companyId(),
                audit.accountBookId()
        );
        if (entity == null) {
            throw new IllegalArgumentException("询价单不存在");
        }
        return entity;
    }

    private void touch(PurchaseInquiryEntity inquiry, AuditMetadata audit) {
        inquiry.setUpdatedBy(audit.userId());
        inquiry.setUpdatedTime(audit.now());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}

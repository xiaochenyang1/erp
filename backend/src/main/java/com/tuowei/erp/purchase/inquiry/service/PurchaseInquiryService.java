package com.tuowei.erp.purchase.inquiry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteLineEntity;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteLineResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseInquiryService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String QUOTE_PENDING = "PENDING";
    private static final String QUOTE_SELECTED = "SELECTED";

    private final PurchaseInquiryMapper purchaseInquiryMapper;
    private final PurchaseInquiryLineMapper purchaseInquiryLineMapper;
    private final PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper;
    private final PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper;
    private final PurchaseInquiryNumberService purchaseInquiryNumberService;
    private final ProductValidator productValidator;
    private final SupplierMapper supplierMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseInquiryService(
            PurchaseInquiryMapper purchaseInquiryMapper,
            PurchaseInquiryLineMapper purchaseInquiryLineMapper,
            PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper,
            PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper,
            PurchaseInquiryNumberService purchaseInquiryNumberService,
            ProductValidator productValidator,
            SupplierMapper supplierMapper,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderService purchaseOrderService
    ) {
        this.purchaseInquiryMapper = purchaseInquiryMapper;
        this.purchaseInquiryLineMapper = purchaseInquiryLineMapper;
        this.purchaseInquiryQuoteMapper = purchaseInquiryQuoteMapper;
        this.purchaseInquiryQuoteLineMapper = purchaseInquiryQuoteLineMapper;
        this.purchaseInquiryNumberService = purchaseInquiryNumberService;
        this.productValidator = productValidator;
        this.supplierMapper = supplierMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderService = purchaseOrderService;
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
        return toResponse(inquiry, lines, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseInquiryResponse> list(PurchaseInquiryPageQuery query) {
        PurchaseInquiryPageQuery safeQuery = query == null ? new PurchaseInquiryPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<PurchaseInquiryEntity> page = new Page<>(pageNo, pageSize);
        Page<PurchaseInquiryEntity> result = purchaseInquiryMapper.selectPage(page, buildListQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PurchaseInquiryResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        return toResponse(inquiry, loadLines(inquiry), loadQuotes(inquiry));
    }

    @Transactional
    public PurchaseInquiryResponse update(Long id, PurchaseInquiryUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
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
        return toResponse(inquiry, lines, loadQuotes(inquiry));
    }

    @Transactional
    public PurchaseInquiryResponse submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        if (!STATUS_DRAFT.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("当前询价单状态不允许提交");
        }
        if (loadLines(inquiry).isEmpty()) {
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
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        if (!STATUS_SUBMITTED.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("仅已提交的询价单可录入报价");
        }
        requireActiveSupplier(request.supplierId(), audit);
        assertNoQuoteForSupplier(inquiry, request.supplierId());

        List<PurchaseInquiryLineEntity> inquiryLines = loadLines(inquiry);
        if (inquiryLines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法录入报价");
        }
        List<QuoteLinePrice> quoteLinePrices = normalizeRequestedQuoteLines(request, inquiryLines);
        LocalDateTime now = audit.now();

        PurchaseInquiryQuoteEntity quote = new PurchaseInquiryQuoteEntity();
        quote.setCompanyId(audit.companyId());
        quote.setAccountBookId(audit.accountBookId());
        quote.setInquiryId(inquiry.getId());
        quote.setSupplierId(request.supplierId());
        // Header pricing is retained only for the single-line legacy contract. Multi-line pricing lives on quote lines.
        quote.setUnitPrice(quoteLinePrices.size() == 1 ? quoteLinePrices.get(0).unitPrice() : null);
        quote.setTaxRate(quoteLinePrices.size() == 1 ? quoteLinePrices.get(0).taxRate() : null);
        quote.setStatus(QUOTE_PENDING);
        quote.setDeletedFlag(0);
        quote.setRemark(trimToNull(request.remark()));
        quote.setCreatedBy(audit.userId());
        quote.setCreatedTime(now);
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(now);
        quote.setVersion(0);
        purchaseInquiryQuoteMapper.insert(quote);
        insertQuoteLines(quote, quoteLinePrices, audit, now);

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
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        if (!STATUS_SUBMITTED.equals(inquiry.getStatus())) {
            throw new IllegalArgumentException("仅已提交的询价单可选定中标报价");
        }
        PurchaseInquiryQuoteEntity quote = requireQuote(request.quoteId(), inquiry, audit);
        if (!QUOTE_PENDING.equals(quote.getStatus())) {
            throw new IllegalArgumentException("报价状态不可选中");
        }
        List<PurchaseInquiryLineEntity> inquiryLines = loadLines(inquiry);
        if (inquiryLines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法选定中标报价");
        }
        resolveQuotePrices(
                inquiry,
                quote,
                inquiryLines,
                "报价缺少单价，无法选定中标报价",
                "报价明细不完整，无法选定中标报价"
        );
        LocalDateTime now = audit.now();

        quote.setStatus(QUOTE_SELECTED);
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseInquiryQuoteMapper.updateById(quote),
                "报价已被其他操作修改，请刷新后重试"
        );

        // 落败报价统一 REJECTED，避免 CLOSED 后仍残留 PENDING 误导运营
        List<PurchaseInquiryQuoteEntity> otherQuotes = purchaseInquiryQuoteMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryQuoteEntity>()
                        .eq(PurchaseInquiryQuoteEntity::getCompanyId, audit.companyId())
                        .eq(PurchaseInquiryQuoteEntity::getAccountBookId, audit.accountBookId())
                        .eq(PurchaseInquiryQuoteEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryQuoteEntity::getDeletedFlag, 0)
                        .eq(PurchaseInquiryQuoteEntity::getStatus, QUOTE_PENDING)
                        .ne(PurchaseInquiryQuoteEntity::getId, quote.getId())
        );
        for (PurchaseInquiryQuoteEntity other : otherQuotes) {
            if (other.getId() == null || Objects.equals(other.getId(), quote.getId())) {
                continue;
            }
            if (!QUOTE_PENDING.equals(other.getStatus())) {
                continue;
            }
            other.setStatus("REJECTED");
            other.setUpdatedBy(audit.userId());
            other.setUpdatedTime(now);
            purchaseInquiryQuoteMapper.updateById(other);
        }

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
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        if ((!STATUS_CLOSED.equals(inquiry.getStatus()) && !STATUS_CONVERTED.equals(inquiry.getStatus()))
                || inquiry.getSelectedQuoteId() == null) {
            throw new IllegalArgumentException("仅已选定中标报价的询价单可生成采购订单预填数据");
        }
        PurchaseInquiryQuoteEntity quote = requireQuote(inquiry.getSelectedQuoteId(), inquiry, audit);
        List<PurchaseInquiryLineEntity> lines = loadLines(inquiry);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法生成采购订单预填数据");
        }
        Map<Long, QuoteLinePrice> quotePrices = resolveQuotePrices(
                inquiry,
                quote,
                lines,
                "中标报价缺少单价，无法生成采购订单预填数据",
                "中标报价明细不完整，无法生成采购订单预填数据"
        );
        String remark = "来源询价单 " + inquiry.getInquiryNo();
        if (StringUtils.hasText(inquiry.getRemark())) {
            remark = remark + "；" + inquiry.getRemark();
        }
        List<PurchaseInquiryPoPrefillResponse.PurchaseInquiryPoPrefillLine> prefillLines = lines.stream()
                .map(line -> {
                    QuoteLinePrice linePrice = quotePrices.get(line.getId());
                    return new PurchaseInquiryPoPrefillResponse.PurchaseInquiryPoPrefillLine(
                            line.getProductId(),
                            ScalePrecision.quantity(line.getQty()),
                            linePrice.unitPrice(),
                            linePrice.taxRate(),
                            line.getRemark()
                    );
                })
                .toList();
        return new PurchaseInquiryPoPrefillResponse(
                inquiry.getId(),
                inquiry.getInquiryNo(),
                quote.getSupplierId(),
                inquiry.getInquiryDate(),
                remark,
                prefillLines
        );
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

        PurchaseInquiryQuoteEntity quote = requireQuote(inquiry.getSelectedQuoteId(), inquiry, audit);
        if (!QUOTE_SELECTED.equals(quote.getStatus())
                || !Objects.equals(quote.getSupplierId(), inquiry.getSelectedSupplierId())) {
            throw new IllegalArgumentException("询价单中标报价无效，无法转换为采购订单");
        }

        List<PurchaseInquiryLineEntity> inquiryLines = loadLines(inquiry);
        if (inquiryLines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法转换为采购订单");
        }
        if (inquiryLines.stream().anyMatch(line -> line.getId() == null)) {
            throw new IllegalArgumentException("询价单明细来源信息不完整");
        }

        Map<Long, QuoteLinePrice> quotePrices = resolveQuotePrices(
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
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
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

    private List<QuoteLinePrice> normalizeRequestedQuoteLines(
            PurchaseInquiryQuoteRequest request,
            List<PurchaseInquiryLineEntity> inquiryLines
    ) {
        List<PurchaseInquiryQuoteLineRequest> requestedLines = request.lines();
        if (requestedLines == null || requestedLines.isEmpty()) {
            if (inquiryLines.size() != 1) {
                throw new IllegalArgumentException("多明细询价单必须逐行提交完整报价");
            }
            return List.of(normalizeQuoteLinePrice(
                    inquiryLines.get(0).getId(),
                    request.unitPrice(),
                    request.taxRate(),
                    "unitPrice不能为空"
            ));
        }

        Set<Long> inquiryLineIds = inquiryLines.stream()
                .map(PurchaseInquiryLineEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (inquiryLineIds.size() != inquiryLines.size()) {
            throw new IllegalArgumentException("询价单明细来源信息不完整");
        }

        Set<Long> requestedLineIds = new HashSet<>();
        List<QuoteLinePrice> prices = new ArrayList<>();
        for (PurchaseInquiryQuoteLineRequest requestedLine : requestedLines) {
            if (requestedLine == null || requestedLine.inquiryLineId() == null) {
                throw new IllegalArgumentException("inquiryLineId不能为空");
            }
            if (!requestedLineIds.add(requestedLine.inquiryLineId())) {
                throw new IllegalArgumentException("报价明细不能重复提交询价行");
            }
            if (!inquiryLineIds.contains(requestedLine.inquiryLineId())) {
                throw new IllegalArgumentException("报价明细不属于当前询价单");
            }
            prices.add(normalizeQuoteLinePrice(
                    requestedLine.inquiryLineId(),
                    requestedLine.unitPrice(),
                    requestedLine.taxRate(),
                    "unitPrice不能为空"
            ));
        }
        if (!requestedLineIds.equals(inquiryLineIds)) {
            throw new IllegalArgumentException("报价明细必须完整覆盖询价单明细");
        }
        return prices;
    }

    private QuoteLinePrice normalizeQuoteLinePrice(
            Long inquiryLineId,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            String missingPriceMessage
    ) {
        if (unitPrice == null) {
            throw new IllegalArgumentException(missingPriceMessage);
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice不能小于0");
        }
        if (taxRate != null && taxRate.signum() < 0) {
            throw new IllegalArgumentException("taxRate不能小于0");
        }
        return new QuoteLinePrice(
                inquiryLineId,
                ScalePrecision.amount(unitPrice),
                ScalePrecision.rate(taxRate == null ? BigDecimal.ZERO : taxRate)
        );
    }

    private void insertQuoteLines(
            PurchaseInquiryQuoteEntity quote,
            List<QuoteLinePrice> prices,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        if (quote.getId() == null) {
            throw new IllegalStateException("报价主表保存失败");
        }
        for (QuoteLinePrice price : prices) {
            PurchaseInquiryQuoteLineEntity line = new PurchaseInquiryQuoteLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setInquiryId(quote.getInquiryId());
            line.setQuoteId(quote.getId());
            line.setInquiryLineId(price.inquiryLineId());
            line.setUnitPrice(price.unitPrice());
            line.setTaxRate(price.taxRate());
            line.setDeletedFlag(0);
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            purchaseInquiryQuoteLineMapper.insert(line);
        }
    }

    private void softDeleteLines(PurchaseInquiryEntity inquiry, AuditMetadata audit, LocalDateTime now) {
        List<PurchaseInquiryLineEntity> existing = loadLines(inquiry);
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

    private void assertNoQuoteForSupplier(PurchaseInquiryEntity inquiry, Long supplierId) {
        boolean exists = purchaseInquiryQuoteMapper.exists(
                new LambdaQueryWrapper<PurchaseInquiryQuoteEntity>()
                        .eq(PurchaseInquiryQuoteEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryQuoteEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryQuoteEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryQuoteEntity::getSupplierId, supplierId)
                        .eq(PurchaseInquiryQuoteEntity::getDeletedFlag, 0)
        );
        if (exists) {
            throw new IllegalArgumentException("该供应商已存在报价");
        }
    }

    private SupplierEntity requireActiveSupplier(Long supplierId, AuditMetadata audit) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || supplier.getDeletedFlag() == null || supplier.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(supplier.getStatus())
                || !Objects.equals(supplier.getCompanyId(), audit.companyId())
                || !Objects.equals(supplier.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("供应商不存在或已停用");
        }
        return supplier;
    }

    private PurchaseInquiryEntity requireInquiry(Long id, AuditMetadata audit) {
        PurchaseInquiryEntity entity = purchaseInquiryMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("询价单不存在");
        }
        return entity;
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

    private PurchaseInquiryQuoteEntity requireQuote(Long quoteId, PurchaseInquiryEntity inquiry, AuditMetadata audit) {
        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteMapper.selectById(quoteId);
        if (quote == null || quote.getDeletedFlag() == null || quote.getDeletedFlag() != 0
                || !Objects.equals(quote.getCompanyId(), audit.companyId())
                || !Objects.equals(quote.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(quote.getInquiryId(), inquiry.getId())) {
            throw new IllegalArgumentException("报价不存在");
        }
        return quote;
    }

    private List<PurchaseInquiryLineEntity> loadLines(PurchaseInquiryEntity inquiry) {
        return purchaseInquiryLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryLineEntity>()
                        .eq(PurchaseInquiryLineEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryLineEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryLineEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryLineEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseInquiryLineEntity::getLineNo)
        );
    }

    private List<PurchaseInquiryQuoteEntity> loadQuotes(PurchaseInquiryEntity inquiry) {
        return purchaseInquiryQuoteMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryQuoteEntity>()
                        .eq(PurchaseInquiryQuoteEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryQuoteEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryQuoteEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryQuoteEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseInquiryQuoteEntity::getId)
        );
    }

    private List<PurchaseInquiryQuoteLineEntity> loadQuoteLines(PurchaseInquiryEntity inquiry) {
        return purchaseInquiryQuoteLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryQuoteLineEntity>()
                        .eq(PurchaseInquiryQuoteLineEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryQuoteLineEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryQuoteLineEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryQuoteLineEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseInquiryQuoteLineEntity::getQuoteId)
                        .orderByAsc(PurchaseInquiryQuoteLineEntity::getInquiryLineId)
        );
    }

    private List<PurchaseInquiryQuoteLineEntity> loadQuoteLines(
            PurchaseInquiryEntity inquiry,
            Long quoteId
    ) {
        return purchaseInquiryQuoteLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryQuoteLineEntity>()
                        .eq(PurchaseInquiryQuoteLineEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryQuoteLineEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryQuoteLineEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryQuoteLineEntity::getQuoteId, quoteId)
                        .eq(PurchaseInquiryQuoteLineEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseInquiryQuoteLineEntity::getInquiryLineId)
        );
    }

    private Map<Long, QuoteLinePrice> resolveQuotePrices(
            PurchaseInquiryEntity inquiry,
            PurchaseInquiryQuoteEntity quote,
            List<PurchaseInquiryLineEntity> inquiryLines,
            String missingPriceMessage,
            String incompleteLinesMessage
    ) {
        List<PurchaseInquiryQuoteLineEntity> quoteLines = loadQuoteLines(inquiry, quote.getId());
        if (quoteLines.isEmpty()) {
            // V127 backfills historical multi-line quotes. Header fallback remains only for legacy single-line data.
            if (inquiryLines.size() != 1) {
                throw new IllegalArgumentException(incompleteLinesMessage);
            }
            QuoteLinePrice headerPrice = normalizeQuoteLinePrice(
                    null,
                    quote.getUnitPrice(),
                    quote.getTaxRate(),
                    missingPriceMessage
            );
            Map<Long, QuoteLinePrice> fallback = new HashMap<>();
            for (PurchaseInquiryLineEntity inquiryLine : inquiryLines) {
                if (inquiryLine.getId() == null) {
                    throw new IllegalArgumentException(incompleteLinesMessage);
                }
                fallback.put(inquiryLine.getId(), new QuoteLinePrice(
                        inquiryLine.getId(),
                        headerPrice.unitPrice(),
                        headerPrice.taxRate()
                ));
            }
            return fallback;
        }

        Set<Long> inquiryLineIds = inquiryLines.stream()
                .map(PurchaseInquiryLineEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (inquiryLineIds.size() != inquiryLines.size() || quoteLines.size() != inquiryLines.size()) {
            throw new IllegalArgumentException(incompleteLinesMessage);
        }

        Map<Long, QuoteLinePrice> prices = new HashMap<>();
        for (PurchaseInquiryQuoteLineEntity quoteLine : quoteLines) {
            if (!Objects.equals(quoteLine.getCompanyId(), inquiry.getCompanyId())
                    || !Objects.equals(quoteLine.getAccountBookId(), inquiry.getAccountBookId())
                    || !Objects.equals(quoteLine.getInquiryId(), inquiry.getId())
                    || !Objects.equals(quoteLine.getQuoteId(), quote.getId())
                    || quoteLine.getDeletedFlag() == null
                    || quoteLine.getDeletedFlag() != 0
                    || !inquiryLineIds.contains(quoteLine.getInquiryLineId())) {
                throw new IllegalArgumentException(incompleteLinesMessage);
            }
            QuoteLinePrice price = normalizeQuoteLinePrice(
                    quoteLine.getInquiryLineId(),
                    quoteLine.getUnitPrice(),
                    quoteLine.getTaxRate(),
                    missingPriceMessage
            );
            if (prices.putIfAbsent(quoteLine.getInquiryLineId(), price) != null) {
                throw new IllegalArgumentException(incompleteLinesMessage);
            }
        }
        if (!prices.keySet().equals(inquiryLineIds)) {
            throw new IllegalArgumentException(incompleteLinesMessage);
        }
        return prices;
    }

    private LambdaQueryWrapper<PurchaseInquiryEntity> buildListQuery(AuditMetadata audit, PurchaseInquiryPageQuery query) {
        LambdaQueryWrapper<PurchaseInquiryEntity> wrapper = new LambdaQueryWrapper<PurchaseInquiryEntity>()
                .eq(PurchaseInquiryEntity::getCompanyId, audit.companyId())
                .eq(PurchaseInquiryEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseInquiryEntity::getDeletedFlag, 0)
                .orderByDesc(PurchaseInquiryEntity::getInquiryDate)
                .orderByDesc(PurchaseInquiryEntity::getId);
        String keyword = trimToNull(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PurchaseInquiryEntity::getInquiryNo, keyword)
                    .or()
                    .like(PurchaseInquiryEntity::getTitle, keyword));
        }
        String status = normalizeStatus(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseInquiryEntity::getStatus, status);
        }
        if (query.getInquiryDateFrom() != null) {
            wrapper.ge(PurchaseInquiryEntity::getInquiryDate, query.getInquiryDateFrom());
        }
        if (query.getInquiryDateTo() != null) {
            wrapper.le(PurchaseInquiryEntity::getInquiryDate, query.getInquiryDateTo());
        }
        return wrapper;
    }

    private void touch(PurchaseInquiryEntity inquiry, AuditMetadata audit) {
        inquiry.setUpdatedBy(audit.userId());
        inquiry.setUpdatedTime(audit.now());
    }

    private PurchaseInquiryResponse toSummaryResponse(PurchaseInquiryEntity entity) {
        return new PurchaseInquiryResponse(
                entity.getId(),
                entity.getInquiryNo(),
                entity.getInquiryDate(),
                entity.getStatus(),
                entity.getSelectedSupplierId(),
                entity.getSelectedQuoteId(),
                entity.getConvertedOrderId(),
                entity.getConvertedOrderNo(),
                entity.getConvertedBy(),
                entity.getConvertedTime(),
                entity.getTitle(),
                entity.getRemark(),
                List.of(),
                List.of()
        );
    }

    private PurchaseInquiryResponse toResponse(
            PurchaseInquiryEntity entity,
            List<PurchaseInquiryLineEntity> lines,
            List<PurchaseInquiryQuoteEntity> quotes
    ) {
        Map<Long, List<PurchaseInquiryQuoteLineEntity>> quoteLinesByQuoteId = quotes.isEmpty()
                ? Map.of()
                : loadQuoteLines(entity).stream().collect(Collectors.groupingBy(
                        PurchaseInquiryQuoteLineEntity::getQuoteId
                ));
        return new PurchaseInquiryResponse(
                entity.getId(),
                entity.getInquiryNo(),
                entity.getInquiryDate(),
                entity.getStatus(),
                entity.getSelectedSupplierId(),
                entity.getSelectedQuoteId(),
                entity.getConvertedOrderId(),
                entity.getConvertedOrderNo(),
                entity.getConvertedBy(),
                entity.getConvertedTime(),
                entity.getTitle(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).collect(Collectors.toList()),
                quotes.stream()
                        .map(quote -> toQuoteResponse(
                                quote,
                                quoteLinesByQuoteId.getOrDefault(quote.getId(), List.of())
                        ))
                        .collect(Collectors.toList())
        );
    }

    private PurchaseInquiryLineResponse toLineResponse(PurchaseInquiryLineEntity line) {
        return new PurchaseInquiryLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getProductId(),
                line.getQty(),
                line.getRemark()
        );
    }

    private PurchaseInquiryQuoteResponse toQuoteResponse(
            PurchaseInquiryQuoteEntity quote,
            List<PurchaseInquiryQuoteLineEntity> quoteLines
    ) {
        return new PurchaseInquiryQuoteResponse(
                quote.getId(),
                quote.getSupplierId(),
                quote.getUnitPrice(),
                quote.getTaxRate(),
                quote.getStatus(),
                quote.getRemark(),
                quoteLines.stream()
                        .map(line -> new PurchaseInquiryQuoteLineResponse(
                                line.getId(),
                                line.getInquiryLineId(),
                                line.getUnitPrice(),
                                line.getTaxRate()
                        ))
                        .toList()
        );
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record QuoteLinePrice(
            Long inquiryLineId,
            BigDecimal unitPrice,
            BigDecimal taxRate
    ) {
    }
}

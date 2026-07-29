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
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService.QuoteLinePrice;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public PurchaseInquiryService(
            PurchaseInquiryMapper purchaseInquiryMapper,
            PurchaseInquiryLineMapper purchaseInquiryLineMapper,
            PurchaseInquiryNumberService purchaseInquiryNumberService,
            ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderService purchaseOrderService,
            PurchaseInquiryQuoteService purchaseInquiryQuoteService
    ) {
        this.purchaseInquiryMapper = purchaseInquiryMapper;
        this.purchaseInquiryLineMapper = purchaseInquiryLineMapper;
        this.purchaseInquiryNumberService = purchaseInquiryNumberService;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseInquiryQuoteService = purchaseInquiryQuoteService;
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
        return toResponse(inquiry, loadLines(inquiry), purchaseInquiryQuoteService.loadQuoteResponses(inquiry));
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
        return toResponse(inquiry, lines, purchaseInquiryQuoteService.loadQuoteResponses(inquiry));
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
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
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
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseInquiryEntity inquiry = requireInquiry(id, audit);
        if ((!STATUS_CLOSED.equals(inquiry.getStatus()) && !STATUS_CONVERTED.equals(inquiry.getStatus()))
                || inquiry.getSelectedQuoteId() == null) {
            throw new IllegalArgumentException("仅已选定中标报价的询价单可生成采购订单预填数据");
        }
        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteService.requireQuote(
                inquiry.getSelectedQuoteId(),
                inquiry,
                audit
        );
        List<PurchaseInquiryLineEntity> lines = loadLines(inquiry);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("询价单没有明细，无法生成采购订单预填数据");
        }
        Map<Long, QuoteLinePrice> quotePrices = purchaseInquiryQuoteService.resolveQuotePrices(
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

        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteService.requireSelectedQuote(inquiry, audit);

        List<PurchaseInquiryLineEntity> inquiryLines = loadLines(inquiry);
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
            List<PurchaseInquiryQuoteResponse> quotes
    ) {
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
                quotes
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

}

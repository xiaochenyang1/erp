package com.tuowei.erp.purchase.inquiry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryQuoteService.QuoteLinePrice;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryLineResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Read-side filtering, tenant validation and response assembly for purchase inquiries. */
@Service
public class PurchaseInquiryQueryService {

    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_CONVERTED = "CONVERTED";

    private final PurchaseInquiryMapper purchaseInquiryMapper;
    private final PurchaseInquiryLineMapper purchaseInquiryLineMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseInquiryQuoteService purchaseInquiryQuoteService;

    public PurchaseInquiryQueryService(
            PurchaseInquiryMapper purchaseInquiryMapper,
            PurchaseInquiryLineMapper purchaseInquiryLineMapper,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseInquiryQuoteService purchaseInquiryQuoteService
    ) {
        this.purchaseInquiryMapper = purchaseInquiryMapper;
        this.purchaseInquiryLineMapper = purchaseInquiryLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseInquiryQuoteService = purchaseInquiryQuoteService;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseInquiryResponse> list(PurchaseInquiryPageQuery query) {
        PurchaseInquiryPageQuery safeQuery = query == null ? new PurchaseInquiryPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<PurchaseInquiryEntity> result = purchaseInquiryMapper.selectPage(
                new Page<>(pageNo, pageSize),
                buildListQuery(audit, safeQuery)
        );
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

    PurchaseInquiryEntity requireInquiry(Long id, AuditMetadata audit) {
        PurchaseInquiryEntity entity = purchaseInquiryMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("询价单不存在");
        }
        return entity;
    }

    List<PurchaseInquiryLineEntity> loadLines(PurchaseInquiryEntity inquiry) {
        return purchaseInquiryLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseInquiryLineEntity>()
                        .eq(PurchaseInquiryLineEntity::getCompanyId, inquiry.getCompanyId())
                        .eq(PurchaseInquiryLineEntity::getAccountBookId, inquiry.getAccountBookId())
                        .eq(PurchaseInquiryLineEntity::getInquiryId, inquiry.getId())
                        .eq(PurchaseInquiryLineEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseInquiryLineEntity::getLineNo)
        );
    }

    PurchaseInquiryResponse toResponse(
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

    private LambdaQueryWrapper<PurchaseInquiryEntity> buildListQuery(
            AuditMetadata audit,
            PurchaseInquiryPageQuery query
    ) {
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

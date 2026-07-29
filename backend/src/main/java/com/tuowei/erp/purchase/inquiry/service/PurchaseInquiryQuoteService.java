package com.tuowei.erp.purchase.inquiry.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteLineMapper;
import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryQuoteMapper;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryLineEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteEntity;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryQuoteLineEntity;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteLineRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteLineResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseInquiryQuoteService {

    private static final String QUOTE_PENDING = "PENDING";
    private static final String QUOTE_SELECTED = "SELECTED";
    private static final String QUOTE_REJECTED = "REJECTED";

    private final PurchaseInquiryLineMapper purchaseInquiryLineMapper;
    private final PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper;
    private final PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper;
    private final SupplierMapper supplierMapper;

    public PurchaseInquiryQuoteService(
            PurchaseInquiryLineMapper purchaseInquiryLineMapper,
            PurchaseInquiryQuoteMapper purchaseInquiryQuoteMapper,
            PurchaseInquiryQuoteLineMapper purchaseInquiryQuoteLineMapper,
            SupplierMapper supplierMapper
    ) {
        this.purchaseInquiryLineMapper = purchaseInquiryLineMapper;
        this.purchaseInquiryQuoteMapper = purchaseInquiryQuoteMapper;
        this.purchaseInquiryQuoteLineMapper = purchaseInquiryQuoteLineMapper;
        this.supplierMapper = supplierMapper;
    }

    void addQuote(
            PurchaseInquiryEntity inquiry,
            PurchaseInquiryQuoteRequest request,
            AuditMetadata audit
    ) {
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
    }

    PurchaseInquiryQuoteEntity selectWinningQuote(
            PurchaseInquiryEntity inquiry,
            Long quoteId,
            AuditMetadata audit
    ) {
        PurchaseInquiryQuoteEntity quote = requireQuote(quoteId, inquiry, audit);
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
            other.setStatus(QUOTE_REJECTED);
            other.setUpdatedBy(audit.userId());
            other.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseInquiryQuoteMapper.updateById(other),
                    "报价已被其他操作修改，请刷新后重试"
            );
        }
        return quote;
    }

    PurchaseInquiryQuoteEntity requireQuote(
            Long quoteId,
            PurchaseInquiryEntity inquiry,
            AuditMetadata audit
    ) {
        PurchaseInquiryQuoteEntity quote = purchaseInquiryQuoteMapper.selectById(quoteId);
        if (quote == null || quote.getDeletedFlag() == null || quote.getDeletedFlag() != 0
                || !Objects.equals(quote.getCompanyId(), audit.companyId())
                || !Objects.equals(quote.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(quote.getInquiryId(), inquiry.getId())) {
            throw new IllegalArgumentException("报价不存在");
        }
        return quote;
    }

    PurchaseInquiryQuoteEntity requireSelectedQuote(
            PurchaseInquiryEntity inquiry,
            AuditMetadata audit
    ) {
        PurchaseInquiryQuoteEntity quote = requireQuote(inquiry.getSelectedQuoteId(), inquiry, audit);
        if (!QUOTE_SELECTED.equals(quote.getStatus())
                || !Objects.equals(quote.getSupplierId(), inquiry.getSelectedSupplierId())) {
            throw new IllegalArgumentException("询价单中标报价无效，无法转换为采购订单");
        }
        return quote;
    }

    Map<Long, QuoteLinePrice> resolveQuotePrices(
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

    List<PurchaseInquiryQuoteResponse> loadQuoteResponses(PurchaseInquiryEntity inquiry) {
        List<PurchaseInquiryQuoteEntity> quotes = loadQuotes(inquiry);
        if (quotes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PurchaseInquiryQuoteLineEntity>> quoteLinesByQuoteId = loadQuoteLines(inquiry).stream()
                .collect(Collectors.groupingBy(PurchaseInquiryQuoteLineEntity::getQuoteId));
        return quotes.stream()
                .map(quote -> toQuoteResponse(
                        quote,
                        quoteLinesByQuoteId.getOrDefault(quote.getId(), List.of())
                ))
                .toList();
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record QuoteLinePrice(
            Long inquiryLineId,
            BigDecimal unitPrice,
            BigDecimal taxRate
    ) {
    }
}

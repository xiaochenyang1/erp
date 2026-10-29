package com.tuowei.erp.sales.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteLineMapper;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteMapper;
import com.tuowei.erp.sales.quote.model.SalesQuoteEntity;
import com.tuowei.erp.sales.quote.model.SalesQuoteLineEntity;
import com.tuowei.erp.sales.quote.web.SalesQuoteLineResponse;
import com.tuowei.erp.sales.quote.web.SalesQuotePageQuery;
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant guards, detail loading and response mapping. */
@Service
public class SalesQuoteQueryService {
    private final SalesQuoteMapper salesQuoteMapper;
    private final SalesQuoteLineMapper salesQuoteLineMapper;
    private final CustomerMapper customerMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SalesQuoteQueryService(SalesQuoteMapper salesQuoteMapper, SalesQuoteLineMapper salesQuoteLineMapper,
                                  CustomerMapper customerMapper, AuditMetadataFactory auditMetadataFactory) {
        this.salesQuoteMapper = salesQuoteMapper;
        this.salesQuoteLineMapper = salesQuoteLineMapper;
        this.customerMapper = customerMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public SalesQuoteResponse detail(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuoteEntity quote = requireQuote(id, audit);
        return toResponse(quote, loadLines(quote), customerName(quote.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesQuoteResponse> list(SalesQuotePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuotePageQuery safe = query == null ? new SalesQuotePageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());
        LambdaQueryWrapper<SalesQuoteEntity> wrapper = new LambdaQueryWrapper<SalesQuoteEntity>()
                .eq(SalesQuoteEntity::getCompanyId, audit.companyId())
                .eq(SalesQuoteEntity::getAccountBookId, audit.accountBookId())
                .eq(SalesQuoteEntity::getDeletedFlag, 0)
                .orderByDesc(SalesQuoteEntity::getId);
        if (StringUtils.hasText(safe.getKeyword())) wrapper.like(SalesQuoteEntity::getQuoteNo, safe.getKeyword().trim());
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(SalesQuoteEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        if (safe.getCustomerId() != null) wrapper.eq(SalesQuoteEntity::getCustomerId, safe.getCustomerId());
        Page<SalesQuoteEntity> page = salesQuoteMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream()
                .map(q -> toResponse(q, List.of(), customerName(q.getCustomerId()))).toList());
    }

    SalesQuoteEntity requireQuote(Long id, AuditMetadata audit) {
        SalesQuoteEntity quote = salesQuoteMapper.selectById(id);
        if (quote == null || !Objects.equals(quote.getCompanyId(), audit.companyId())
                || !Objects.equals(quote.getAccountBookId(), audit.accountBookId())
                || (quote.getDeletedFlag() != null && quote.getDeletedFlag() != 0)) {
            throw new IllegalArgumentException("报价单不存在");
        }
        return quote;
    }

    List<SalesQuoteLineEntity> loadLines(SalesQuoteEntity quote) {
        return salesQuoteLineMapper.selectList(new LambdaQueryWrapper<SalesQuoteLineEntity>()
                .eq(SalesQuoteLineEntity::getCompanyId, quote.getCompanyId())
                .eq(SalesQuoteLineEntity::getAccountBookId, quote.getAccountBookId())
                .eq(SalesQuoteLineEntity::getQuoteId, quote.getId())
                .eq(SalesQuoteLineEntity::getDeletedFlag, 0)
                .orderByAsc(SalesQuoteLineEntity::getLineNo));
    }

    SalesQuoteResponse toResponse(SalesQuoteEntity quote, List<SalesQuoteLineEntity> lines, String customerName) {
        return new SalesQuoteResponse(quote.getId(), quote.getQuoteNo(), quote.getCustomerId(), customerName,
                quote.getQuoteDate(), quote.getValidUntil(), quote.getStatus(), quote.getTotalAmount(), quote.getTotalTaxAmount(),
                quote.getConvertedOrderId(), quote.getRemark(), lines.stream().map(line -> new SalesQuoteLineResponse(
                        line.getId(), line.getLineNo(), line.getProductId(), line.getQty(), line.getPrice(), line.getTaxRate(),
                        line.getAmount(), line.getTaxAmount(), line.getRemark())).toList());
    }

    private String customerName(Long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }
}

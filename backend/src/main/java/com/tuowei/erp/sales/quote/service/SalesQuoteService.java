package com.tuowei.erp.sales.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteLineMapper;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteMapper;
import com.tuowei.erp.sales.quote.model.SalesQuoteEntity;
import com.tuowei.erp.sales.quote.model.SalesQuoteLineEntity;
import com.tuowei.erp.sales.quote.web.SalesQuoteLineRequest;
import com.tuowei.erp.sales.quote.web.SalesQuoteLineResponse;
import com.tuowei.erp.sales.quote.web.SalesQuotePageQuery;
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class SalesQuoteService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final SalesQuoteMapper salesQuoteMapper;
    private final SalesQuoteLineMapper salesQuoteLineMapper;
    private final SalesQuoteNumberService salesQuoteNumberService;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final SalesOrderService salesOrderService;
    private final AuditMetadataFactory auditMetadataFactory;

    public SalesQuoteService(
            SalesQuoteMapper salesQuoteMapper,
            SalesQuoteLineMapper salesQuoteLineMapper,
            SalesQuoteNumberService salesQuoteNumberService,
            CustomerMapper customerMapper,
            ProductValidator productValidator,
            SalesOrderService salesOrderService,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.salesQuoteMapper = salesQuoteMapper;
        this.salesQuoteLineMapper = salesQuoteLineMapper;
        this.salesQuoteNumberService = salesQuoteNumberService;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.salesOrderService = salesOrderService;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public SalesQuoteResponse create(SalesQuoteSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireCustomer(request.customerId(), audit);
        List<SalesQuoteLineRequest> lines = requireLines(request.lines(), audit);
        LocalDateTime now = audit.now();

        SalesQuoteEntity quote = new SalesQuoteEntity();
        quote.setCompanyId(audit.companyId());
        quote.setAccountBookId(audit.accountBookId());
        quote.setQuoteNo(salesQuoteNumberService.nextQuoteNo(request.quoteDate(), audit));
        quote.setCustomerId(customer.getId());
        quote.setQuoteDate(request.quoteDate());
        quote.setValidUntil(request.validUntil());
        quote.setStatus(STATUS_DRAFT);
        quote.setDeletedFlag(0);
        quote.setRemark(trim(request.remark()));
        fillCreateAudit(quote, audit, now);
        Totals totals = calcTotals(lines);
        quote.setTotalAmount(totals.amount());
        quote.setTotalTaxAmount(totals.tax());
        salesQuoteMapper.insert(quote);
        insertLines(quote, lines, audit, now);
        return detail(quote.getId());
    }

    @Transactional
    public SalesQuoteResponse update(Long id, SalesQuoteSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuoteEntity quote = requireQuote(id, audit);
        if (!STATUS_DRAFT.equals(quote.getStatus())) {
            throw new IllegalArgumentException("仅草稿报价可编辑");
        }
        CustomerEntity customer = requireCustomer(request.customerId(), audit);
        List<SalesQuoteLineRequest> lines = requireLines(request.lines(), audit);
        LocalDateTime now = audit.now();
        quote.setCustomerId(customer.getId());
        quote.setQuoteDate(request.quoteDate());
        quote.setValidUntil(request.validUntil());
        quote.setRemark(trim(request.remark()));
        Totals totals = calcTotals(lines);
        quote.setTotalAmount(totals.amount());
        quote.setTotalTaxAmount(totals.tax());
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新");
        softDeleteLines(quote, audit, now);
        insertLines(quote, lines, audit, now);
        return detail(id);
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
        if (StringUtils.hasText(safe.getKeyword())) {
            wrapper.like(SalesQuoteEntity::getQuoteNo, safe.getKeyword().trim());
        }
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(SalesQuoteEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (safe.getCustomerId() != null) {
            wrapper.eq(SalesQuoteEntity::getCustomerId, safe.getCustomerId());
        }
        Page<SalesQuoteEntity> page = salesQuoteMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResponse<>(
                page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream()
                        .map(q -> toResponse(q, List.of(), customerName(q.getCustomerId())))
                        .toList()
        );
    }

    @Transactional
    public SalesQuoteResponse confirm(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuoteEntity quote = requireQuote(id, audit);
        if (!STATUS_DRAFT.equals(quote.getStatus())) {
            throw new IllegalArgumentException("仅草稿可确认");
        }
        quote.setStatus(STATUS_CONFIRMED);
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新");
        return detail(id);
    }

    @Transactional
    public SalesQuoteResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuoteEntity quote = requireQuote(id, audit);
        if (STATUS_CONVERTED.equals(quote.getStatus()) || STATUS_CANCELLED.equals(quote.getStatus())) {
            throw new IllegalArgumentException("当前状态不可作废");
        }
        quote.setStatus(STATUS_CANCELLED);
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新");
        return detail(id);
    }

    @Transactional
    public SalesOrderResponse convertToOrder(Long id, Long warehouseId) {
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        SalesQuoteEntity quote = requireQuote(id, audit);
        if (!STATUS_CONFIRMED.equals(quote.getStatus())) {
            throw new IllegalArgumentException("仅已确认报价可转销售订单");
        }
        List<SalesQuoteLineEntity> lines = loadLines(quote);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("报价明细为空");
        }
        List<SalesOrderLineRequest> soLines = lines.stream()
                .map(line -> new SalesOrderLineRequest(
                        line.getProductId(),
                        line.getQty(),
                        line.getPrice(),
                        ScalePrecision.rate(ScalePrecision.zeroDefault(line.getTaxRate())),
                        line.getRemark()
                ))
                .toList();
        SalesOrderResponse order = salesOrderService.create(new SalesOrderCreateRequest(
                quote.getCustomerId(),
                warehouseId,
                quote.getQuoteDate(),
                quote.getValidUntil(),
                "来源报价 " + quote.getQuoteNo() + (StringUtils.hasText(quote.getRemark()) ? "；" + quote.getRemark() : ""),
                soLines
        ));
        quote.setStatus(STATUS_CONVERTED);
        quote.setConvertedOrderId(order.id());
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新");
        return order;
    }

    private void insertLines(SalesQuoteEntity quote, List<SalesQuoteLineRequest> lines, AuditMetadata audit, LocalDateTime now) {
        int no = 1;
        for (SalesQuoteLineRequest req : lines) {
            SalesQuoteLineEntity line = new SalesQuoteLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setQuoteId(quote.getId());
            line.setLineNo(no++);
            line.setProductId(req.productId());
            line.setQty(ScalePrecision.quantity(req.qty()));
            line.setPrice(ScalePrecision.amount(req.price()));
            BigDecimal taxRate = ScalePrecision.rate(ScalePrecision.zeroDefault(req.taxRate()));
            line.setTaxRate(taxRate);
            BigDecimal amount = ScalePrecision.amount(req.qty().multiply(req.price()));
            line.setAmount(amount);
            line.setTaxAmount(ScalePrecision.taxAmount(amount, taxRate.compareTo(BigDecimal.ONE) > 0 ? taxRate : taxRate.multiply(new BigDecimal("100"))));
            // if taxRate is 0.13 style, taxAmount formula divides by 100 - so use percent 13 when rate < 1
            if (taxRate.compareTo(BigDecimal.ONE) <= 0) {
                line.setTaxAmount(ScalePrecision.taxAmount(amount, taxRate.multiply(new BigDecimal("100"))));
            }
            line.setDeletedFlag(0);
            line.setRemark(trim(req.remark()));
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            salesQuoteLineMapper.insert(line);
        }
    }

    private void softDeleteLines(SalesQuoteEntity quote, AuditMetadata audit, LocalDateTime now) {
        for (SalesQuoteLineEntity line : loadLines(quote)) {
            line.setDeletedFlag(1);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            salesQuoteLineMapper.updateById(line);
        }
    }

    private List<SalesQuoteLineEntity> loadLines(SalesQuoteEntity quote) {
        return salesQuoteLineMapper.selectList(new LambdaQueryWrapper<SalesQuoteLineEntity>()
                .eq(SalesQuoteLineEntity::getCompanyId, quote.getCompanyId())
                .eq(SalesQuoteLineEntity::getAccountBookId, quote.getAccountBookId())
                .eq(SalesQuoteLineEntity::getQuoteId, quote.getId())
                .eq(SalesQuoteLineEntity::getDeletedFlag, 0)
                .orderByAsc(SalesQuoteLineEntity::getLineNo));
    }

    private List<SalesQuoteLineRequest> requireLines(List<SalesQuoteLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("明细不能为空");
        }
        for (SalesQuoteLineRequest line : lines) {
            productValidator.requireProduct(line.productId(), audit.companyId(), audit.accountBookId());
        }
        return lines;
    }

    private CustomerEntity requireCustomer(Long customerId, AuditMetadata audit) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null
                || !Objects.equals(customer.getCompanyId(), audit.companyId())
                || !Objects.equals(customer.getAccountBookId(), audit.accountBookId())
                || (customer.getDeletedFlag() != null && customer.getDeletedFlag() != 0)
                || !"ACTIVE".equalsIgnoreCase(String.valueOf(customer.getStatus()))) {
            throw new IllegalArgumentException("客户不存在或已停用");
        }
        return customer;
    }

    private SalesQuoteEntity requireQuote(Long id, AuditMetadata audit) {
        SalesQuoteEntity quote = salesQuoteMapper.selectById(id);
        if (quote == null
                || !Objects.equals(quote.getCompanyId(), audit.companyId())
                || !Objects.equals(quote.getAccountBookId(), audit.accountBookId())
                || (quote.getDeletedFlag() != null && quote.getDeletedFlag() != 0)) {
            throw new IllegalArgumentException("报价单不存在");
        }
        return quote;
    }

    private Totals calcTotals(List<SalesQuoteLineRequest> lines) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SalesQuoteLineRequest line : lines) {
            BigDecimal lineAmount = ScalePrecision.amount(line.qty().multiply(line.price()));
            BigDecimal rate = ScalePrecision.zeroDefault(line.taxRate());
            BigDecimal taxRatePercent = rate.compareTo(BigDecimal.ONE) > 0 ? rate : rate.multiply(new BigDecimal("100"));
            amount = amount.add(lineAmount);
            tax = tax.add(ScalePrecision.taxAmount(lineAmount, taxRatePercent));
        }
        return new Totals(ScalePrecision.amount(amount), ScalePrecision.amount(tax));
    }

    private SalesQuoteResponse toResponse(SalesQuoteEntity quote, List<SalesQuoteLineEntity> lines, String customerName) {
        return new SalesQuoteResponse(
                quote.getId(),
                quote.getQuoteNo(),
                quote.getCustomerId(),
                customerName,
                quote.getQuoteDate(),
                quote.getValidUntil(),
                quote.getStatus(),
                quote.getTotalAmount(),
                quote.getTotalTaxAmount(),
                quote.getConvertedOrderId(),
                quote.getRemark(),
                lines.stream().map(line -> new SalesQuoteLineResponse(
                        line.getId(), line.getLineNo(), line.getProductId(), line.getQty(), line.getPrice(),
                        line.getTaxRate(), line.getAmount(), line.getTaxAmount(), line.getRemark()
                )).toList()
        );
    }

    private String customerName(Long customerId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        return customer == null ? null : customer.getCustomerName();
    }

    private void fillCreateAudit(SalesQuoteEntity quote, AuditMetadata audit, LocalDateTime now) {
        quote.setCreatedBy(audit.userId());
        quote.setCreatedTime(now);
        quote.setUpdatedBy(audit.userId());
        quote.setUpdatedTime(now);
        quote.setVersion(0);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Totals(BigDecimal amount, BigDecimal tax) {
    }
}

package com.tuowei.erp.sales.quote.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
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
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Write-side creation, lifecycle commands and conversion for sales quotes. */
@Service
public class SalesQuoteCommandService {
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
    private final SalesQuoteQueryService queryService;

    public SalesQuoteCommandService(SalesQuoteMapper salesQuoteMapper, SalesQuoteLineMapper salesQuoteLineMapper,
                                    SalesQuoteNumberService salesQuoteNumberService, CustomerMapper customerMapper,
                                    ProductValidator productValidator, SalesOrderService salesOrderService,
                                    AuditMetadataFactory auditMetadataFactory, SalesQuoteQueryService queryService) {
        this.salesQuoteMapper = salesQuoteMapper;
        this.salesQuoteLineMapper = salesQuoteLineMapper;
        this.salesQuoteNumberService = salesQuoteNumberService;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.salesOrderService = salesOrderService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public SalesQuoteResponse create(SalesQuoteSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireCustomer(request.customerId(), audit);
        List<SalesQuoteLineRequest> lines = requireLines(request.lines(), audit);
        LocalDateTime now = audit.now();
        SalesQuoteEntity quote = new SalesQuoteEntity();
        quote.setCompanyId(audit.companyId()); quote.setAccountBookId(audit.accountBookId());
        quote.setQuoteNo(salesQuoteNumberService.nextQuoteNo(request.quoteDate(), audit)); quote.setCustomerId(customer.getId());
        quote.setQuoteDate(request.quoteDate()); quote.setValidUntil(request.validUntil()); quote.setStatus(STATUS_DRAFT);
        quote.setDeletedFlag(0); quote.setRemark(trim(request.remark())); fillCreateAudit(quote, audit, now);
        Totals totals = calcTotals(lines); quote.setTotalAmount(totals.amount()); quote.setTotalTaxAmount(totals.tax());
        salesQuoteMapper.insert(quote); insertLines(quote, lines, audit, now);
        return queryService.detail(quote.getId());
    }

    @Transactional
    public SalesQuoteResponse update(Long id, SalesQuoteSaveRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); SalesQuoteEntity quote = queryService.requireQuote(id, audit);
        if (!STATUS_DRAFT.equals(quote.getStatus())) throw new IllegalArgumentException("仅草稿报价可编辑");
        CustomerEntity customer = requireCustomer(request.customerId(), audit); List<SalesQuoteLineRequest> lines = requireLines(request.lines(), audit);
        LocalDateTime now = audit.now(); quote.setCustomerId(customer.getId()); quote.setQuoteDate(request.quoteDate()); quote.setValidUntil(request.validUntil()); quote.setRemark(trim(request.remark()));
        Totals totals = calcTotals(lines); quote.setTotalAmount(totals.amount()); quote.setTotalTaxAmount(totals.tax()); quote.setUpdatedBy(audit.userId()); quote.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新"); softDeleteLines(quote, audit, now); insertLines(quote, lines, audit, now);
        return queryService.detail(id);
    }

    @Transactional
    public SalesQuoteResponse confirm(Long id) {
        AuditMetadata audit = auditMetadataFactory.current(); SalesQuoteEntity quote = queryService.requireQuote(id, audit);
        if (!STATUS_DRAFT.equals(quote.getStatus())) throw new IllegalArgumentException("仅草稿可确认");
        quote.setStatus(STATUS_CONFIRMED); touch(quote, audit);
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新"); return queryService.detail(id);
    }

    @Transactional
    public SalesQuoteResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current(); SalesQuoteEntity quote = queryService.requireQuote(id, audit);
        if (STATUS_CONVERTED.equals(quote.getStatus()) || STATUS_CANCELLED.equals(quote.getStatus())) throw new IllegalArgumentException("当前状态不可作废");
        quote.setStatus(STATUS_CANCELLED); touch(quote, audit);
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新"); return queryService.detail(id);
    }

    @Transactional
    public SalesOrderResponse convertToOrder(Long id, Long warehouseId) {
        if (warehouseId == null) throw new IllegalArgumentException("warehouseId不能为空");
        AuditMetadata audit = auditMetadataFactory.current(); SalesQuoteEntity quote = queryService.requireQuote(id, audit);
        if (!STATUS_CONFIRMED.equals(quote.getStatus())) throw new IllegalArgumentException("仅已确认报价可转销售订单");
        List<SalesQuoteLineEntity> lines = queryService.loadLines(quote); if (lines.isEmpty()) throw new IllegalArgumentException("报价明细为空");
        List<SalesOrderLineRequest> orderLines = lines.stream().map(line -> new SalesOrderLineRequest(line.getProductId(), line.getQty(), line.getPrice(),
                ScalePrecision.rate(ScalePrecision.zeroDefault(line.getTaxRate())), line.getRemark())).toList();
        SalesOrderResponse order = salesOrderService.create(new SalesOrderCreateRequest(quote.getCustomerId(), warehouseId, quote.getQuoteDate(), quote.getValidUntil(),
                "来源报价 " + quote.getQuoteNo() + (StringUtils.hasText(quote.getRemark()) ? "；" + quote.getRemark() : ""), orderLines));
        quote.setStatus(STATUS_CONVERTED); quote.setConvertedOrderId(order.id()); touch(quote, audit);
        OptimisticLockGuard.requireUpdated(salesQuoteMapper.updateById(quote), "报价单已被修改，请刷新"); return order;
    }

    private void insertLines(SalesQuoteEntity quote, List<SalesQuoteLineRequest> lines, AuditMetadata audit, LocalDateTime now) {
        int no = 1;
        for (SalesQuoteLineRequest req : lines) {
            SalesQuoteLineEntity line = new SalesQuoteLineEntity(); line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId()); line.setQuoteId(quote.getId()); line.setLineNo(no++);
            line.setProductId(req.productId()); line.setQty(ScalePrecision.quantity(req.qty())); line.setPrice(ScalePrecision.amount(req.price()));
            BigDecimal taxRate = ScalePrecision.rate(ScalePrecision.zeroDefault(req.taxRate())); line.setTaxRate(taxRate); BigDecimal amount = ScalePrecision.amount(req.qty().multiply(req.price())); line.setAmount(amount);
            line.setTaxAmount(ScalePrecision.taxAmount(amount, taxRate.compareTo(BigDecimal.ONE) > 0 ? taxRate : taxRate.multiply(new BigDecimal("100")))); line.setDeletedFlag(0); line.setRemark(trim(req.remark()));
            line.setCreatedBy(audit.userId()); line.setCreatedTime(now); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); line.setVersion(0); salesQuoteLineMapper.insert(line);
        }
    }

    private void softDeleteLines(SalesQuoteEntity quote, AuditMetadata audit, LocalDateTime now) {
        for (SalesQuoteLineEntity line : queryService.loadLines(quote)) { line.setDeletedFlag(1); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); salesQuoteLineMapper.updateById(line); }
    }
    private List<SalesQuoteLineRequest> requireLines(List<SalesQuoteLineRequest> lines, AuditMetadata audit) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("明细不能为空");
        for (SalesQuoteLineRequest line : lines) productValidator.requireProduct(line.productId(), audit.companyId(), audit.accountBookId());
        return lines;
    }
    private CustomerEntity requireCustomer(Long customerId, AuditMetadata audit) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || !Objects.equals(customer.getCompanyId(), audit.companyId()) || !Objects.equals(customer.getAccountBookId(), audit.accountBookId())
                || (customer.getDeletedFlag() != null && customer.getDeletedFlag() != 0) || !"ACTIVE".equalsIgnoreCase(String.valueOf(customer.getStatus()))) throw new IllegalArgumentException("客户不存在或已停用");
        return customer;
    }
    private Totals calcTotals(List<SalesQuoteLineRequest> lines) {
        BigDecimal amount = BigDecimal.ZERO; BigDecimal tax = BigDecimal.ZERO;
        for (SalesQuoteLineRequest line : lines) { BigDecimal lineAmount = ScalePrecision.amount(line.qty().multiply(line.price())); BigDecimal rate = ScalePrecision.zeroDefault(line.taxRate()); BigDecimal percent = rate.compareTo(BigDecimal.ONE) > 0 ? rate : rate.multiply(new BigDecimal("100")); amount = amount.add(lineAmount); tax = tax.add(ScalePrecision.taxAmount(lineAmount, percent)); }
        return new Totals(ScalePrecision.amount(amount), ScalePrecision.amount(tax));
    }
    private void fillCreateAudit(SalesQuoteEntity quote, AuditMetadata audit, LocalDateTime now) { quote.setCreatedBy(audit.userId()); quote.setCreatedTime(now); quote.setUpdatedBy(audit.userId()); quote.setUpdatedTime(now); quote.setVersion(0); }
    private void touch(SalesQuoteEntity quote, AuditMetadata audit) { quote.setUpdatedBy(audit.userId()); quote.setUpdatedTime(audit.now()); }
    private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private record Totals(BigDecimal amount, BigDecimal tax) { }
}

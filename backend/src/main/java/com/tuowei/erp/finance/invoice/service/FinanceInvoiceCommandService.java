package com.tuowei.erp.finance.invoice.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.model.InvoiceRegisterEntity;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import com.tuowei.erp.finance.invoice.web.InvoiceUpdateRequest;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.finance.invoice.service.InvoiceNumberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FinanceInvoiceCommandService {

    private static final Set<String> INVOICE_TYPES = Set.of("INPUT", "OUTPUT");
    private static final String DRAFT = "DRAFT";
    private static final String POSTED = "POSTED";
    private static final String CANCELLED = "CANCELLED";

    private final InvoiceRegisterMapper invoiceRegisterMapper;
    private final InvoiceNumberService invoiceNumberService;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentService attachmentService;
    private final FinanceInvoiceQueryService queryService;

    public FinanceInvoiceCommandService(
            InvoiceRegisterMapper invoiceRegisterMapper,
            InvoiceNumberService invoiceNumberService,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentService attachmentService,
            FinanceInvoiceQueryService queryService
    ) {
        this.invoiceRegisterMapper = invoiceRegisterMapper;
        this.invoiceNumberService = invoiceNumberService;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentService = attachmentService;
        this.queryService = queryService;
    }

    @Transactional
    public InvoiceResponse create(InvoiceCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String invoiceType = normalizeInvoiceType(request.invoiceType());
        String partnerName = normalizeRequired(request.partnerName(), "往来单位不能为空");
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal taxAmount = ScalePrecision.amount(request.taxAmount() == null ? BigDecimal.ZERO : request.taxAmount());
        ensurePositive(amount, "发票金额必须大于0");
        ensureNonNegative(taxAmount, "税额不能为负");
        validateRelatedBiz(request.relatedBizType(), request.relatedBizId(), audit);
        InvoiceRegisterEntity entity = new InvoiceRegisterEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId());
        entity.setInvoiceNo(invoiceNumberService.nextInvoiceNo(request.invoiceDate()));
        entity.setInvoiceType(invoiceType); entity.setPartnerName(partnerName); entity.setAmount(amount); entity.setTaxAmount(taxAmount);
        entity.setInvoiceDate(request.invoiceDate()); entity.setRelatedBizType(trimToNull(request.relatedBizType())); entity.setRelatedBizId(request.relatedBizId());
        entity.setStatus(DRAFT); entity.setDeletedFlag(0); entity.setRemark(trimToNull(request.remark())); setAudit(entity, audit, now);
        invoiceRegisterMapper.insert(entity);
        return FinanceInvoiceQueryService.toResponse(entity);
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceUpdateRequest request) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (!DRAFT.equals(entity.getStatus())) throw new IllegalArgumentException("只有草稿状态的发票登记可以编辑");
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setInvoiceType(normalizeInvoiceType(request.invoiceType()));
        entity.setPartnerName(normalizeRequired(request.partnerName(), "往来单位不能为空"));
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal taxAmount = ScalePrecision.amount(request.taxAmount() == null ? BigDecimal.ZERO : request.taxAmount());
        ensurePositive(amount, "发票金额必须大于0"); ensureNonNegative(taxAmount, "税额不能为负");
        entity.setAmount(amount); entity.setTaxAmount(taxAmount); entity.setInvoiceDate(request.invoiceDate());
        validateRelatedBiz(request.relatedBizType(), request.relatedBizId(), audit);
        entity.setRelatedBizType(trimToNull(request.relatedBizType())); entity.setRelatedBizId(request.relatedBizId()); entity.setRemark(trimToNull(request.remark()));
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return queryService.detail(id);
    }

    @Transactional
    public InvoiceResponse post(Long id) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (POSTED.equals(entity.getStatus())) return FinanceInvoiceQueryService.toResponse(entity);
        if (!DRAFT.equals(entity.getStatus())) throw new IllegalArgumentException("只有草稿状态的发票登记可以确认");
        attachmentService.requireIfConfigured(AttachmentBusinessType.FIN_INVOICE, entity.getId());
        touch(entity);
        entity.setStatus(POSTED);
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return queryService.detail(id);
    }

    @Transactional
    public InvoiceResponse cancel(Long id) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (CANCELLED.equals(entity.getStatus())) return FinanceInvoiceQueryService.toResponse(entity);
        if (!DRAFT.equals(entity.getStatus()) && !POSTED.equals(entity.getStatus())) throw new IllegalArgumentException("当前状态不可作废");
        touch(entity); entity.setStatus(CANCELLED);
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return queryService.detail(id);
    }

    private InvoiceRegisterEntity requireInvoice(Long id) { return queryService.requireInvoice(id); }
    private void touch(InvoiceRegisterEntity entity) { AuditMetadata audit = auditMetadataFactory.current(); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now()); }
    private String normalizeInvoiceType(String value) { String normalized = normalizeRequired(value, "发票类型不能为空").toUpperCase(Locale.ROOT); if (!INVOICE_TYPES.contains(normalized)) throw new IllegalArgumentException("发票类型仅支持 INPUT/OUTPUT"); return normalized; }
    private String normalizeRequired(String value, String message) { if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message); return value.trim(); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private void ensurePositive(BigDecimal value, String message) { if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(message); }
    private void ensureNonNegative(BigDecimal value, String message) { if (value == null || value.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(message); }
    private void validateRelatedBiz(String relatedBizType, Long relatedBizId, AuditMetadata audit) {
        String type = trimToNull(relatedBizType); if (type == null && relatedBizId == null) return; if (type == null || relatedBizId == null) throw new IllegalArgumentException("关联业务类型与关联业务ID须同时填写或同时为空");
        String upper = type.toUpperCase(Locale.ROOT);
        if ("PURCHASE_ORDER".equals(upper)) { PurchaseOrderEntity po = purchaseOrderMapper.selectById(relatedBizId); if (po == null || !Objects.equals(po.getCompanyId(), audit.companyId()) || !Objects.equals(po.getAccountBookId(), audit.accountBookId())) throw new IllegalArgumentException("关联采购订单不存在"); return; }
        if ("SALES_ORDER".equals(upper)) { SalesOrderEntity so = salesOrderMapper.selectById(relatedBizId); if (so == null || !Objects.equals(so.getCompanyId(), audit.companyId()) || !Objects.equals(so.getAccountBookId(), audit.accountBookId())) throw new IllegalArgumentException("关联销售订单不存在"); return; }
        throw new IllegalArgumentException("关联业务类型仅支持 PURCHASE_ORDER 或 SALES_ORDER");
    }
    private void setAudit(InvoiceRegisterEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); }
}

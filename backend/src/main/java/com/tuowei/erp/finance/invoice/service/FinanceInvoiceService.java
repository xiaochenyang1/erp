package com.tuowei.erp.finance.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.model.InvoiceRegisterEntity;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoicePageQuery;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import com.tuowei.erp.finance.invoice.web.InvoiceUpdateRequest;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FinanceInvoiceService {

    private static final Set<String> INVOICE_TYPES = Set.of("INPUT", "OUTPUT");
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final InvoiceRegisterMapper invoiceRegisterMapper;
    private final InvoiceNumberService invoiceNumberService;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentService attachmentService;

    public FinanceInvoiceService(
            InvoiceRegisterMapper invoiceRegisterMapper,
            InvoiceNumberService invoiceNumberService,
            PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentService attachmentService
    ) {
        this.invoiceRegisterMapper = invoiceRegisterMapper;
        this.invoiceNumberService = invoiceNumberService;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentService = attachmentService;
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
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setInvoiceNo(invoiceNumberService.nextInvoiceNo(request.invoiceDate()));
        entity.setInvoiceType(invoiceType);
        entity.setPartnerName(partnerName);
        entity.setAmount(amount);
        entity.setTaxAmount(taxAmount);
        entity.setInvoiceDate(request.invoiceDate());
        entity.setRelatedBizType(trimToNull(request.relatedBizType()));
        entity.setRelatedBizId(request.relatedBizId());
        entity.setStatus(STATUS_DRAFT);
        entity.setDeletedFlag(0);
        entity.setRemark(trimToNull(request.remark()));
        setAudit(entity, audit, now);
        invoiceRegisterMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(InvoicePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InvoicePageQuery safeQuery = query == null ? new InvoicePageQuery() : query;
        Page<InvoiceRegisterEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InvoiceRegisterEntity> wrapper = new LambdaQueryWrapper<InvoiceRegisterEntity>()
                .eq(InvoiceRegisterEntity::getCompanyId, audit.companyId())
                .eq(InvoiceRegisterEntity::getAccountBookId, audit.accountBookId())
                .eq(InvoiceRegisterEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(InvoiceRegisterEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getInvoiceType())) {
            wrapper.eq(InvoiceRegisterEntity::getInvoiceType, normalizeInvoiceType(safeQuery.getInvoiceType()));
        }
        if (StringUtils.hasText(safeQuery.getPartnerName())) {
            wrapper.like(InvoiceRegisterEntity::getPartnerName, safeQuery.getPartnerName().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(InvoiceRegisterEntity::getInvoiceDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(InvoiceRegisterEntity::getInvoiceDate, safeQuery.getDateTo());
        }
        wrapper.orderByDesc(InvoiceRegisterEntity::getInvoiceDate).orderByDesc(InvoiceRegisterEntity::getId);
        Page<InvoiceRegisterEntity> result = invoiceRegisterMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public InvoiceResponse detail(Long id) {
        return toResponse(requireInvoice(id));
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceUpdateRequest request) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的发票登记可以编辑");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        String invoiceType = normalizeInvoiceType(request.invoiceType());
        String partnerName = normalizeRequired(request.partnerName(), "往来单位不能为空");
        BigDecimal amount = ScalePrecision.amount(request.amount());
        BigDecimal taxAmount = ScalePrecision.amount(request.taxAmount() == null ? BigDecimal.ZERO : request.taxAmount());
        ensurePositive(amount, "发票金额必须大于0");
        ensureNonNegative(taxAmount, "税额不能为负");

        entity.setInvoiceType(invoiceType);
        entity.setPartnerName(partnerName);
        entity.setAmount(amount);
        entity.setTaxAmount(taxAmount);
        entity.setInvoiceDate(request.invoiceDate());
        validateRelatedBiz(request.relatedBizType(), request.relatedBizId(), audit);
        entity.setRelatedBizType(trimToNull(request.relatedBizType()));
        entity.setRelatedBizId(request.relatedBizId());
        entity.setRemark(trimToNull(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    @Transactional
    public InvoiceResponse post(Long id) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (STATUS_POSTED.equals(entity.getStatus())) {
            return toResponse(entity);
        }
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的发票登记可以确认");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.FIN_INVOICE, entity.getId());
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(STATUS_POSTED);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    @Transactional
    public InvoiceResponse cancel(Long id) {
        InvoiceRegisterEntity entity = requireInvoice(id);
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            return toResponse(entity);
        }
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_POSTED.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前状态不可作废");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(STATUS_CANCELLED);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(invoiceRegisterMapper.updateById(entity), "发票登记已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    private InvoiceRegisterEntity requireInvoice(Long id) {
        InvoiceRegisterEntity entity = invoiceRegisterMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("发票登记不存在");
        }
        return entity;
    }

    private InvoiceResponse toResponse(InvoiceRegisterEntity entity) {
        return new InvoiceResponse(
                entity.getId(),
                entity.getInvoiceNo(),
                entity.getInvoiceType(),
                entity.getPartnerName(),
                entity.getAmount(),
                entity.getTaxAmount(),
                entity.getInvoiceDate(),
                entity.getRelatedBizType(),
                entity.getRelatedBizId(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String normalizeInvoiceType(String invoiceType) {
        String normalized = normalizeRequired(invoiceType, "发票类型不能为空").toUpperCase(Locale.ROOT);
        if (!INVOICE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("发票类型仅支持 INPUT/OUTPUT");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void validateRelatedBiz(String relatedBizType, Long relatedBizId, AuditMetadata audit) {
        String type = trimToNull(relatedBizType);
        if (type == null && relatedBizId == null) {
            return;
        }
        if (type == null || relatedBizId == null) {
            throw new IllegalArgumentException("关联业务类型与关联业务ID须同时填写或同时为空");
        }
        String upper = type.toUpperCase(Locale.ROOT);
        if ("PURCHASE_ORDER".equals(upper)) {
            PurchaseOrderEntity po = purchaseOrderMapper.selectById(relatedBizId);
            if (po == null || !Objects.equals(po.getCompanyId(), audit.companyId())) {
                throw new IllegalArgumentException("关联采购订单不存在");
            }
            return;
        }
        if ("SALES_ORDER".equals(upper)) {
            SalesOrderEntity so = salesOrderMapper.selectById(relatedBizId);
            if (so == null || !Objects.equals(so.getCompanyId(), audit.companyId())) {
                throw new IllegalArgumentException("关联销售订单不存在");
            }
            return;
        }
        throw new IllegalArgumentException("关联业务类型仅支持 PURCHASE_ORDER 或 SALES_ORDER");
    }

    private void ensurePositive(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureNonNegative(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void setAudit(InvoiceRegisterEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

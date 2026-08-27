package com.tuowei.erp.finance.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.invoice.mapper.InvoiceRegisterMapper;
import com.tuowei.erp.finance.invoice.model.InvoiceRegisterEntity;
import com.tuowei.erp.finance.invoice.web.InvoicePageQuery;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class FinanceInvoiceQueryService {

    private final InvoiceRegisterMapper invoiceRegisterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FinanceInvoiceQueryService(
            InvoiceRegisterMapper invoiceRegisterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.invoiceRegisterMapper = invoiceRegisterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(InvoicePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InvoicePageQuery safeQuery = query == null ? new InvoicePageQuery() : query;
        Page<InvoiceRegisterEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<InvoiceRegisterEntity> wrapper = new LambdaQueryWrapper<InvoiceRegisterEntity>()
                .eq(InvoiceRegisterEntity::getCompanyId, audit.companyId())
                .eq(InvoiceRegisterEntity::getAccountBookId, audit.accountBookId())
                .eq(InvoiceRegisterEntity::getDeletedFlag, 0);
        if (hasText(safeQuery.getStatus())) {
            wrapper.eq(InvoiceRegisterEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(safeQuery.getInvoiceType())) {
            wrapper.eq(InvoiceRegisterEntity::getInvoiceType, normalizeInvoiceType(safeQuery.getInvoiceType()));
        }
        if (hasText(safeQuery.getPartnerName())) {
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
        List<InvoiceResponse> responses = result.getRecords().stream().map(FinanceInvoiceQueryService::toResponse).toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), responses);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse detail(Long id) {
        return toResponse(requireInvoice(id));
    }

    InvoiceRegisterEntity requireInvoice(Long id) {
        InvoiceRegisterEntity entity = invoiceRegisterMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("发票登记不存在");
        }
        return entity;
    }

    static InvoiceResponse toResponse(InvoiceRegisterEntity entity) {
        return new InvoiceResponse(entity.getId(), entity.getInvoiceNo(), entity.getInvoiceType(), entity.getPartnerName(),
                entity.getAmount(), entity.getTaxAmount(), entity.getInvoiceDate(), entity.getRelatedBizType(),
                entity.getRelatedBizId(), entity.getStatus(), entity.getRemark());
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    private String normalizeInvoiceType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"INPUT".equals(normalized) && !"OUTPUT".equals(normalized)) {
            throw new IllegalArgumentException("发票类型仅支持 INPUT/OUTPUT");
        }
        return normalized;
    }

    private long normalizePageNo(Integer value) { return value == null || value < 1 ? 1L : value; }
    private long normalizePageSize(Integer value) { return value == null || value < 1 ? 20L : Math.min(value, 200); }
}

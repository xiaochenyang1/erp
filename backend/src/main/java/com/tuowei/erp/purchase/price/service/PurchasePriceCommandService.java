package com.tuowei.erp.purchase.price.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.price.mapper.PurchasePriceMapper;
import com.tuowei.erp.purchase.price.model.PurchasePriceEntity;
import com.tuowei.erp.purchase.price.web.PurchasePriceCreateRequest;
import com.tuowei.erp.purchase.price.web.PurchasePriceResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Write-side creation, updates, validation and status commands for purchase prices. */
@Service
public class PurchasePriceCommandService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);
    private final PurchasePriceMapper purchasePriceMapper;
    private final SupplierMapper supplierMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchasePriceQueryService queryService;

    public PurchasePriceCommandService(PurchasePriceMapper purchasePriceMapper, SupplierMapper supplierMapper,
                                       ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory,
                                       PurchasePriceQueryService queryService) {
        this.purchasePriceMapper = purchasePriceMapper; this.supplierMapper = supplierMapper; this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory; this.queryService = queryService;
    }

    @Transactional
    public PurchasePriceResponse create(PurchasePriceCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); validatePayload(request.supplierId(), request.productId(), request.listPrice(), request.maxPrice(), request.effectiveFrom(), request.effectiveTo(), audit);
        LocalDateTime now = audit.now(); PurchasePriceEntity entity = new PurchasePriceEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId()); entity.setSupplierId(request.supplierId()); entity.setProductId(request.productId());
        entity.setListPrice(ScalePrecision.amount(request.listPrice())); entity.setMaxPrice(ScalePrecision.amount(request.maxPrice())); entity.setEffectiveFrom(request.effectiveFrom()); entity.setEffectiveTo(request.effectiveTo());
        entity.setStatus(STATUS_ACTIVE); entity.setDeletedFlag(0); entity.setRemark(trimToNull(request.remark())); entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
        purchasePriceMapper.insert(entity); return queryService.toResponse(entity);
    }

    @Transactional
    public PurchasePriceResponse update(Long id, PurchasePriceUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchasePriceEntity entity = queryService.requirePrice(id, audit);
        validatePayload(request.supplierId(), request.productId(), request.listPrice(), request.maxPrice(), request.effectiveFrom(), request.effectiveTo(), audit);
        entity.setSupplierId(request.supplierId()); entity.setProductId(request.productId()); entity.setListPrice(ScalePrecision.amount(request.listPrice())); entity.setMaxPrice(ScalePrecision.amount(request.maxPrice())); entity.setEffectiveFrom(request.effectiveFrom()); entity.setEffectiveTo(request.effectiveTo());
        if (StringUtils.hasText(request.status())) entity.setStatus(normalizeStatus(request.status())); entity.setRemark(trimToNull(request.remark())); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(purchasePriceMapper.updateById(entity), "采购价目已被其他操作修改，请刷新后重试"); return queryService.toResponse(entity);
    }

    @Transactional public PurchasePriceResponse enable(Long id) { return toggleStatus(id, STATUS_ACTIVE); }
    @Transactional public PurchasePriceResponse disable(Long id) { return toggleStatus(id, STATUS_INACTIVE); }
    private PurchasePriceResponse toggleStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchasePriceEntity entity = queryService.requirePrice(id, audit); entity.setStatus(status); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(purchasePriceMapper.updateById(entity), "采购价目已被其他操作修改，请刷新后重试"); return queryService.toResponse(entity);
    }
    private void validatePayload(Long supplierId, Long productId, BigDecimal listPrice, BigDecimal maxPrice, LocalDate effectiveFrom, LocalDate effectiveTo, AuditMetadata audit) {
        productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());
        if (supplierId != null) {
            SupplierEntity supplier = supplierMapper.selectById(supplierId);
            if (supplier == null || !Objects.equals(supplier.getCompanyId(), audit.companyId()) || !Objects.equals(supplier.getAccountBookId(), audit.accountBookId()) || (supplier.getDeletedFlag() != null && supplier.getDeletedFlag() != 0)) throw new IllegalArgumentException("供应商不存在或不属于当前账套");
            if (!STATUS_ACTIVE.equalsIgnoreCase(String.valueOf(supplier.getStatus()))) throw new IllegalArgumentException("供应商未启用，不能配置专价");
        }
        if (listPrice == null || listPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("标准价不能小于0");
        if (maxPrice == null || maxPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("最高价不能小于0");
        if (maxPrice.compareTo(listPrice) < 0) throw new IllegalArgumentException("最高价不能低于标准价");
        if (effectiveFrom == null) throw new IllegalArgumentException("生效日期不能为空");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) throw new IllegalArgumentException("失效日期不能早于生效日期");
    }
    private String normalizeStatus(String status) { String upper = status.trim().toUpperCase(Locale.ROOT); if (!STATUSES.contains(upper)) throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE"); return upper; }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}

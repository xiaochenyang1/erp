package com.tuowei.erp.sales.price.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.price.mapper.SalesPriceMapper;
import com.tuowei.erp.sales.price.model.SalesPriceEntity;
import com.tuowei.erp.sales.price.web.SalesPriceCreateRequest;
import com.tuowei.erp.sales.price.web.SalesPriceResponse;
import com.tuowei.erp.sales.price.web.SalesPriceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Write-side creation, updates, validation and status commands for sales prices. */
@Service
public class SalesPriceCommandService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);

    private final SalesPriceMapper salesPriceMapper;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesPriceQueryService queryService;

    public SalesPriceCommandService(SalesPriceMapper salesPriceMapper, CustomerMapper customerMapper,
                                    ProductValidator productValidator, AuditMetadataFactory auditMetadataFactory,
                                    SalesPriceQueryService queryService) {
        this.salesPriceMapper = salesPriceMapper;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public SalesPriceResponse create(SalesPriceCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validatePayload(request.customerId(), request.productId(), request.listPrice(), request.minPrice(), request.effectiveFrom(), request.effectiveTo(), audit);
        LocalDateTime now = audit.now();
        SalesPriceEntity entity = new SalesPriceEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId());
        entity.setCustomerId(request.customerId()); entity.setProductId(request.productId());
        entity.setListPrice(ScalePrecision.amount(request.listPrice())); entity.setMinPrice(ScalePrecision.amount(request.minPrice()));
        entity.setEffectiveFrom(request.effectiveFrom()); entity.setEffectiveTo(request.effectiveTo()); entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0); entity.setRemark(trimToNull(request.remark())); entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
        salesPriceMapper.insert(entity);
        return queryService.toResponse(entity);
    }

    @Transactional
    public SalesPriceResponse update(Long id, SalesPriceUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesPriceEntity entity = queryService.requirePrice(id, audit);
        validatePayload(request.customerId(), request.productId(), request.listPrice(), request.minPrice(), request.effectiveFrom(), request.effectiveTo(), audit);
        entity.setCustomerId(request.customerId()); entity.setProductId(request.productId());
        entity.setListPrice(ScalePrecision.amount(request.listPrice())); entity.setMinPrice(ScalePrecision.amount(request.minPrice()));
        entity.setEffectiveFrom(request.effectiveFrom()); entity.setEffectiveTo(request.effectiveTo());
        if (StringUtils.hasText(request.status())) entity.setStatus(normalizeStatus(request.status()));
        entity.setRemark(trimToNull(request.remark())); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesPriceMapper.updateById(entity), "销售价目已被其他操作修改，请刷新后重试");
        return queryService.toResponse(entity);
    }

    @Transactional public SalesPriceResponse enable(Long id) { return toggleStatus(id, STATUS_ACTIVE); }
    @Transactional public SalesPriceResponse disable(Long id) { return toggleStatus(id, STATUS_INACTIVE); }

    private SalesPriceResponse toggleStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current(); SalesPriceEntity entity = queryService.requirePrice(id, audit);
        entity.setStatus(status); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(salesPriceMapper.updateById(entity), "销售价目已被其他操作修改，请刷新后重试");
        return queryService.toResponse(entity);
    }

    private void validatePayload(Long customerId, Long productId, BigDecimal listPrice, BigDecimal minPrice,
                                 LocalDate effectiveFrom, LocalDate effectiveTo, AuditMetadata audit) {
        productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());
        if (customerId != null) {
            CustomerEntity customer = customerMapper.selectById(customerId);
            if (customer == null || !Objects.equals(customer.getCompanyId(), audit.companyId())
                    || !Objects.equals(customer.getAccountBookId(), audit.accountBookId())
                    || (customer.getDeletedFlag() != null && customer.getDeletedFlag() != 0)) throw new IllegalArgumentException("客户不存在或不属于当前账套");
            if (!STATUS_ACTIVE.equalsIgnoreCase(String.valueOf(customer.getStatus()))) throw new IllegalArgumentException("客户未启用，不能配置专价");
        }
        if (listPrice == null || listPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("标准价不能小于0");
        if (minPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("最低价不能小于0");
        if (minPrice.compareTo(listPrice) > 0) throw new IllegalArgumentException("最低价不能高于标准价");
        if (effectiveFrom == null) throw new IllegalArgumentException("生效日期不能为空");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) throw new IllegalArgumentException("失效日期不能早于生效日期");
    }

    private String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(upper)) throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE");
        return upper;
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}

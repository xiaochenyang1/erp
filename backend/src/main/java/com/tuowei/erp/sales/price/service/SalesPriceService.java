package com.tuowei.erp.sales.price.service;

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
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.price.mapper.SalesPriceMapper;
import com.tuowei.erp.sales.price.model.SalesPriceEntity;
import com.tuowei.erp.sales.price.web.SalesPriceCreateRequest;
import com.tuowei.erp.sales.price.web.SalesPricePageQuery;
import com.tuowei.erp.sales.price.web.SalesPriceResolveResponse;
import com.tuowei.erp.sales.price.web.SalesPriceResponse;
import com.tuowei.erp.sales.price.web.SalesPriceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesPriceService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);

    private final SalesPriceMapper salesPriceMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;

    public SalesPriceService(
            SalesPriceMapper salesPriceMapper,
            ProductMapper productMapper,
            CustomerMapper customerMapper,
            ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.salesPriceMapper = salesPriceMapper;
        this.productMapper = productMapper;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public SalesPriceResponse create(SalesPriceCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validatePayload(request.customerId(), request.productId(), request.listPrice(), request.minPrice(),
                request.effectiveFrom(), request.effectiveTo(), audit);
        LocalDateTime now = audit.now();

        SalesPriceEntity entity = new SalesPriceEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setCustomerId(request.customerId());
        entity.setProductId(request.productId());
        entity.setListPrice(ScalePrecision.amount(request.listPrice()));
        entity.setMinPrice(ScalePrecision.amount(request.minPrice()));
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(trimToNull(request.remark()));
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        salesPriceMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public SalesPriceResponse update(Long id, SalesPriceUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesPriceEntity entity = requirePrice(id, audit);
        validatePayload(request.customerId(), request.productId(), request.listPrice(), request.minPrice(),
                request.effectiveFrom(), request.effectiveTo(), audit);
        LocalDateTime now = audit.now();

        entity.setCustomerId(request.customerId());
        entity.setProductId(request.productId());
        entity.setListPrice(ScalePrecision.amount(request.listPrice()));
        entity.setMinPrice(ScalePrecision.amount(request.minPrice()));
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(normalizeStatus(request.status()));
        }
        entity.setRemark(trimToNull(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesPriceMapper.updateById(entity),
                "销售价目已被其他操作修改，请刷新后重试"
        );
        return toResponse(entity);
    }

    @Transactional
    public SalesPriceResponse enable(Long id) {
        return toggleStatus(id, STATUS_ACTIVE);
    }

    @Transactional
    public SalesPriceResponse disable(Long id) {
        return toggleStatus(id, STATUS_INACTIVE);
    }

    @Transactional(readOnly = true)
    public SalesPriceResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requirePrice(id, audit));
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesPriceResponse> list(SalesPricePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesPricePageQuery safe = query == null ? new SalesPricePageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());

        LambdaQueryWrapper<SalesPriceEntity> wrapper = new LambdaQueryWrapper<SalesPriceEntity>()
                .eq(SalesPriceEntity::getCompanyId, audit.companyId())
                .eq(SalesPriceEntity::getAccountBookId, audit.accountBookId())
                .eq(SalesPriceEntity::getDeletedFlag, 0)
                .orderByDesc(SalesPriceEntity::getUpdatedTime)
                .orderByDesc(SalesPriceEntity::getId);
        if (safe.getCustomerId() != null) {
            wrapper.eq(SalesPriceEntity::getCustomerId, safe.getCustomerId());
        }
        if (safe.getProductId() != null) {
            wrapper.eq(SalesPriceEntity::getProductId, safe.getProductId());
        }
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(SalesPriceEntity::getStatus, normalizeStatus(safe.getStatus()));
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            // 关键字按商品编码/名称反查 productId 集合
            List<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                            .eq(ProductEntity::getCompanyId, audit.companyId())
                            .eq(ProductEntity::getAccountBookId, audit.accountBookId())
                            .eq(ProductEntity::getDeletedFlag, 0)
                            .and(w -> w.like(ProductEntity::getProductCode, safe.getKeyword().trim())
                                    .or()
                                    .like(ProductEntity::getProductName, safe.getKeyword().trim())))
                    .stream()
                    .map(ProductEntity::getId)
                    .toList();
            if (productIds.isEmpty()) {
                return new PageResponse<>(pageNo, pageSize, 0, List.of());
            }
            wrapper.in(SalesPriceEntity::getProductId, productIds);
        }

        Page<SalesPriceEntity> page = salesPriceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, ProductEntity> products = loadProducts(page.getRecords(), audit);
        Map<Long, CustomerEntity> customers = loadCustomers(page.getRecords(), audit);
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(item -> toResponse(item, products, customers)).toList()
        );
    }

    /**
     * 解析生效价目：客户专价优先，其次商品通用价。
     */
    @Transactional(readOnly = true)
    public SalesPriceResolveResponse resolve(Long customerId, Long productId, LocalDate bizDate) {
        AuditMetadata audit = auditMetadataFactory.current();
        if (productId == null) {
            throw new IllegalArgumentException("productId不能为空");
        }
        LocalDate date = bizDate == null ? LocalDate.now() : bizDate;
        productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());

        SalesPriceEntity matched = findBestMatch(audit.companyId(), audit.accountBookId(), customerId, productId, date);
        if (matched == null) {
            return new SalesPriceResolveResponse(productId, customerId, date, false, "NONE", null, null, null);
        }
        String level = matched.getCustomerId() == null ? "PRODUCT" : "CUSTOMER";
        return new SalesPriceResolveResponse(
                productId,
                customerId,
                date,
                true,
                level,
                matched.getId(),
                ScalePrecision.amount(matched.getListPrice()),
                ScalePrecision.amount(matched.getMinPrice())
        );
    }

    /**
     * 供订单校验：返回生效最低价；无匹配返回 null（不拦截）。
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveMinPrice(Long companyId, Long accountBookId, Long customerId, Long productId, LocalDate bizDate) {
        SalesPriceEntity matched = findBestMatch(companyId, accountBookId, customerId, productId, bizDate);
        return matched == null ? null : ScalePrecision.amount(matched.getMinPrice());
    }

    private SalesPriceEntity findBestMatch(
            Long companyId,
            Long accountBookId,
            Long customerId,
            Long productId,
            LocalDate bizDate
    ) {
        if (customerId != null) {
            SalesPriceEntity customerPrice = pickLatest(listEffective(companyId, accountBookId, customerId, productId, bizDate));
            if (customerPrice != null) {
                return customerPrice;
            }
        }
        return pickLatest(listEffective(companyId, accountBookId, null, productId, bizDate));
    }

    private List<SalesPriceEntity> listEffective(
            Long companyId,
            Long accountBookId,
            Long customerId,
            Long productId,
            LocalDate bizDate
    ) {
        LambdaQueryWrapper<SalesPriceEntity> wrapper = new LambdaQueryWrapper<SalesPriceEntity>()
                .eq(SalesPriceEntity::getCompanyId, companyId)
                .eq(SalesPriceEntity::getAccountBookId, accountBookId)
                .eq(SalesPriceEntity::getProductId, productId)
                .eq(SalesPriceEntity::getStatus, STATUS_ACTIVE)
                .eq(SalesPriceEntity::getDeletedFlag, 0)
                .le(SalesPriceEntity::getEffectiveFrom, bizDate)
                .and(w -> w.isNull(SalesPriceEntity::getEffectiveTo).or().ge(SalesPriceEntity::getEffectiveTo, bizDate));
        if (customerId == null) {
            wrapper.isNull(SalesPriceEntity::getCustomerId);
        } else {
            wrapper.eq(SalesPriceEntity::getCustomerId, customerId);
        }
        return salesPriceMapper.selectList(wrapper);
    }

    private SalesPriceEntity pickLatest(List<SalesPriceEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .sorted((a, b) -> {
                    int byFrom = b.getEffectiveFrom().compareTo(a.getEffectiveFrom());
                    if (byFrom != 0) {
                        return byFrom;
                    }
                    return Long.compare(
                            b.getId() == null ? 0L : b.getId(),
                            a.getId() == null ? 0L : a.getId()
                    );
                })
                .findFirst()
                .orElse(null);
    }

    private SalesPriceResponse toggleStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesPriceEntity entity = requirePrice(id, audit);
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                salesPriceMapper.updateById(entity),
                "销售价目已被其他操作修改，请刷新后重试"
        );
        return toResponse(entity);
    }

    private void validatePayload(
            Long customerId,
            Long productId,
            BigDecimal listPrice,
            BigDecimal minPrice,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            AuditMetadata audit
    ) {
        productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());
        if (customerId != null) {
            CustomerEntity customer = customerMapper.selectById(customerId);
            if (customer == null
                    || !Objects.equals(customer.getCompanyId(), audit.companyId())
                    || !Objects.equals(customer.getAccountBookId(), audit.accountBookId())
                    || (customer.getDeletedFlag() != null && customer.getDeletedFlag() != 0)) {
                throw new IllegalArgumentException("客户不存在或不属于当前账套");
            }
            if (!"ACTIVE".equalsIgnoreCase(String.valueOf(customer.getStatus()))) {
                throw new IllegalArgumentException("客户未启用，不能配置专价");
            }
        }
        if (listPrice == null || listPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("标准价不能小于0");
        }
        if (minPrice == null || minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("最低价不能小于0");
        }
        if (minPrice.compareTo(listPrice) > 0) {
            throw new IllegalArgumentException("最低价不能高于标准价");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("生效日期不能为空");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
    }

    private SalesPriceEntity requirePrice(Long id, AuditMetadata audit) {
        SalesPriceEntity entity = salesPriceMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || (entity.getDeletedFlag() != null && entity.getDeletedFlag() != 0)) {
            throw new IllegalArgumentException("销售价目不存在");
        }
        return entity;
    }

    private Map<Long, ProductEntity> loadProducts(List<SalesPriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(SalesPriceEntity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(p -> Objects.equals(p.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a, HashMap::new));
    }

    private Map<Long, CustomerEntity> loadCustomers(List<SalesPriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(SalesPriceEntity::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(ids).stream()
                .filter(c -> Objects.equals(c.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(CustomerEntity::getId, c -> c, (a, b) -> a, HashMap::new));
    }

    private SalesPriceResponse toResponse(SalesPriceEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        Map<Long, ProductEntity> products = loadProducts(List.of(entity), audit);
        Map<Long, CustomerEntity> customers = loadCustomers(List.of(entity), audit);
        return toResponse(entity, products, customers);
    }

    private SalesPriceResponse toResponse(
            SalesPriceEntity entity,
            Map<Long, ProductEntity> products,
            Map<Long, CustomerEntity> customers
    ) {
        ProductEntity product = products.get(entity.getProductId());
        CustomerEntity customer = entity.getCustomerId() == null ? null : customers.get(entity.getCustomerId());
        return new SalesPriceResponse(
                entity.getId(),
                entity.getCustomerId(),
                customer == null ? null : customer.getCustomerName(),
                entity.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                ScalePrecision.amount(entity.getListPrice()),
                ScalePrecision.amount(entity.getMinPrice()),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(upper)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE");
        }
        return upper;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

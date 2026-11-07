package com.tuowei.erp.sales.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.tuowei.erp.sales.price.web.SalesPricePageQuery;
import com.tuowei.erp.sales.price.web.SalesPriceResolveResponse;
import com.tuowei.erp.sales.price.web.SalesPriceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-side filtering, tenant guards, price resolution and response mapping. */
@Service
public class SalesPriceQueryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SalesPriceMapper salesPriceMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;

    public SalesPriceQueryService(
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
        if (safe.getCustomerId() != null) wrapper.eq(SalesPriceEntity::getCustomerId, safe.getCustomerId());
        if (safe.getProductId() != null) wrapper.eq(SalesPriceEntity::getProductId, safe.getProductId());
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(SalesPriceEntity::getStatus, normalizeStatus(safe.getStatus()));
        if (StringUtils.hasText(safe.getKeyword())) {
            List<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                            .eq(ProductEntity::getCompanyId, audit.companyId())
                            .eq(ProductEntity::getAccountBookId, audit.accountBookId())
                            .eq(ProductEntity::getDeletedFlag, 0)
                            .and(w -> w.like(ProductEntity::getProductCode, safe.getKeyword().trim())
                                    .or().like(ProductEntity::getProductName, safe.getKeyword().trim())))
                    .stream().map(ProductEntity::getId).toList();
            if (productIds.isEmpty()) return new PageResponse<>(pageNo, pageSize, 0, List.of());
            wrapper.in(SalesPriceEntity::getProductId, productIds);
        }
        Page<SalesPriceEntity> page = salesPriceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, ProductEntity> products = loadProducts(page.getRecords(), audit);
        Map<Long, CustomerEntity> customers = loadCustomers(page.getRecords(), audit);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(item -> toResponse(item, products, customers)).toList());
    }

    @Transactional(readOnly = true)
    public SalesPriceResolveResponse resolve(Long customerId, Long productId, LocalDate bizDate) {
        AuditMetadata audit = auditMetadataFactory.current();
        if (productId == null) throw new IllegalArgumentException("productId不能为空");
        LocalDate date = bizDate == null ? LocalDate.now() : bizDate;
        productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());
        SalesPriceEntity matched = findBestMatch(audit.companyId(), audit.accountBookId(), customerId, productId, date);
        if (matched == null) return new SalesPriceResolveResponse(productId, customerId, date, false, "NONE", null, null, null);
        String level = matched.getCustomerId() == null ? "PRODUCT" : "CUSTOMER";
        return new SalesPriceResolveResponse(productId, customerId, date, true, level, matched.getId(),
                ScalePrecision.amount(matched.getListPrice()), ScalePrecision.amount(matched.getMinPrice()));
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveMinPrice(Long companyId, Long accountBookId, Long customerId, Long productId, LocalDate bizDate) {
        SalesPriceEntity matched = findBestMatch(companyId, accountBookId, customerId, productId, bizDate);
        return matched == null ? null : ScalePrecision.amount(matched.getMinPrice());
    }

    SalesPriceEntity requirePrice(Long id, AuditMetadata audit) {
        SalesPriceEntity entity = salesPriceMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || (entity.getDeletedFlag() != null && entity.getDeletedFlag() != 0)) {
            throw new IllegalArgumentException("销售价目不存在");
        }
        return entity;
    }

    SalesPriceResponse toResponse(SalesPriceEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(entity, loadProducts(List.of(entity), audit), loadCustomers(List.of(entity), audit));
    }

    private SalesPriceEntity findBestMatch(Long companyId, Long accountBookId, Long customerId, Long productId, LocalDate bizDate) {
        if (customerId != null) {
            SalesPriceEntity customerPrice = pickLatest(listEffective(companyId, accountBookId, customerId, productId, bizDate));
            if (customerPrice != null) return customerPrice;
        }
        return pickLatest(listEffective(companyId, accountBookId, null, productId, bizDate));
    }

    private List<SalesPriceEntity> listEffective(Long companyId, Long accountBookId, Long customerId, Long productId, LocalDate bizDate) {
        LambdaQueryWrapper<SalesPriceEntity> wrapper = new LambdaQueryWrapper<SalesPriceEntity>()
                .eq(SalesPriceEntity::getCompanyId, companyId).eq(SalesPriceEntity::getAccountBookId, accountBookId)
                .eq(SalesPriceEntity::getProductId, productId).eq(SalesPriceEntity::getStatus, STATUS_ACTIVE)
                .eq(SalesPriceEntity::getDeletedFlag, 0).le(SalesPriceEntity::getEffectiveFrom, bizDate)
                .and(w -> w.isNull(SalesPriceEntity::getEffectiveTo).or().ge(SalesPriceEntity::getEffectiveTo, bizDate));
        if (customerId == null) wrapper.isNull(SalesPriceEntity::getCustomerId);
        else wrapper.eq(SalesPriceEntity::getCustomerId, customerId);
        return salesPriceMapper.selectList(wrapper);
    }

    private SalesPriceEntity pickLatest(List<SalesPriceEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream().sorted((a, b) -> {
            int byFrom = b.getEffectiveFrom().compareTo(a.getEffectiveFrom());
            if (byFrom != 0) return byFrom;
            return Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId());
        }).findFirst().orElse(null);
    }

    private Map<Long, ProductEntity> loadProducts(List<SalesPriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(SalesPriceEntity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(ids).stream().filter(p -> Objects.equals(p.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a, HashMap::new));
    }

    private Map<Long, CustomerEntity> loadCustomers(List<SalesPriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(SalesPriceEntity::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return customerMapper.selectBatchIds(ids).stream().filter(c -> Objects.equals(c.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(CustomerEntity::getId, c -> c, (a, b) -> a, HashMap::new));
    }

    private SalesPriceResponse toResponse(SalesPriceEntity entity, Map<Long, ProductEntity> products, Map<Long, CustomerEntity> customers) {
        ProductEntity product = products.get(entity.getProductId());
        CustomerEntity customer = entity.getCustomerId() == null ? null : customers.get(entity.getCustomerId());
        return new SalesPriceResponse(entity.getId(), entity.getCustomerId(), customer == null ? null : customer.getCustomerName(),
                entity.getProductId(), product == null ? null : product.getProductCode(), product == null ? null : product.getProductName(),
                ScalePrecision.amount(entity.getListPrice()), ScalePrecision.amount(entity.getMinPrice()), entity.getEffectiveFrom(),
                entity.getEffectiveTo(), entity.getStatus(), entity.getRemark());
    }

    private String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(upper)) throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE");
        return upper;
    }
}

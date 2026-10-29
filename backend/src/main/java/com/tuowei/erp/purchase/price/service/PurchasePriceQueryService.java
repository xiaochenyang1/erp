package com.tuowei.erp.purchase.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.price.mapper.PurchasePriceMapper;
import com.tuowei.erp.purchase.price.model.PurchasePriceEntity;
import com.tuowei.erp.purchase.price.web.PurchasePricePageQuery;
import com.tuowei.erp.purchase.price.web.PurchasePriceResolveResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceResponse;
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
public class PurchasePriceQueryService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private final PurchasePriceMapper purchasePriceMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;

    public PurchasePriceQueryService(PurchasePriceMapper purchasePriceMapper, ProductMapper productMapper,
                                     SupplierMapper supplierMapper, ProductValidator productValidator,
                                     AuditMetadataFactory auditMetadataFactory) {
        this.purchasePriceMapper = purchasePriceMapper; this.productMapper = productMapper; this.supplierMapper = supplierMapper;
        this.productValidator = productValidator; this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PurchasePriceResponse getById(Long id) { AuditMetadata audit = auditMetadataFactory.current(); return toResponse(requirePrice(id, audit)); }

    @Transactional(readOnly = true)
    public PageResponse<PurchasePriceResponse> list(PurchasePricePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current(); PurchasePricePageQuery safe = query == null ? new PurchasePricePageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());
        LambdaQueryWrapper<PurchasePriceEntity> wrapper = new LambdaQueryWrapper<PurchasePriceEntity>()
                .eq(PurchasePriceEntity::getCompanyId, audit.companyId()).eq(PurchasePriceEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchasePriceEntity::getDeletedFlag, 0).orderByDesc(PurchasePriceEntity::getUpdatedTime).orderByDesc(PurchasePriceEntity::getId);
        if (safe.getSupplierId() != null) wrapper.eq(PurchasePriceEntity::getSupplierId, safe.getSupplierId());
        if (safe.getProductId() != null) wrapper.eq(PurchasePriceEntity::getProductId, safe.getProductId());
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(PurchasePriceEntity::getStatus, normalizeStatus(safe.getStatus()));
        if (StringUtils.hasText(safe.getKeyword())) {
            List<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                            .eq(ProductEntity::getCompanyId, audit.companyId()).eq(ProductEntity::getAccountBookId, audit.accountBookId())
                            .eq(ProductEntity::getDeletedFlag, 0).and(w -> w.like(ProductEntity::getProductCode, safe.getKeyword().trim())
                                    .or().like(ProductEntity::getProductName, safe.getKeyword().trim())))
                    .stream().map(ProductEntity::getId).toList();
            if (productIds.isEmpty()) return new PageResponse<>(pageNo, pageSize, 0, List.of());
            wrapper.in(PurchasePriceEntity::getProductId, productIds);
        }
        Page<PurchasePriceEntity> page = purchasePriceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, ProductEntity> products = loadProducts(page.getRecords(), audit); Map<Long, SupplierEntity> suppliers = loadSuppliers(page.getRecords(), audit);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(item -> toResponse(item, products, suppliers)).toList());
    }

    @Transactional(readOnly = true)
    public PurchasePriceResolveResponse resolve(Long supplierId, Long productId, LocalDate bizDate) {
        AuditMetadata audit = auditMetadataFactory.current(); if (productId == null) throw new IllegalArgumentException("productId不能为空");
        LocalDate date = bizDate == null ? LocalDate.now() : bizDate; productValidator.requireProduct(productId, audit.companyId(), audit.accountBookId());
        PurchasePriceEntity matched = findBestMatch(audit.companyId(), audit.accountBookId(), supplierId, productId, date);
        if (matched == null) return new PurchasePriceResolveResponse(productId, supplierId, date, false, "NONE", null, null, null);
        String level = matched.getSupplierId() == null ? "PRODUCT" : "SUPPLIER";
        return new PurchasePriceResolveResponse(productId, supplierId, date, true, level, matched.getId(), ScalePrecision.amount(matched.getListPrice()), ScalePrecision.amount(matched.getMaxPrice()));
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveMaxPrice(Long companyId, Long accountBookId, Long supplierId, Long productId, LocalDate bizDate) {
        PurchasePriceEntity matched = findBestMatch(companyId, accountBookId, supplierId, productId, bizDate);
        return matched == null ? null : ScalePrecision.amount(matched.getMaxPrice());
    }

    PurchasePriceEntity requirePrice(Long id, AuditMetadata audit) {
        PurchasePriceEntity entity = purchasePriceMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getCompanyId(), audit.companyId()) || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())
                || (entity.getDeletedFlag() != null && entity.getDeletedFlag() != 0)) throw new IllegalArgumentException("采购价目不存在");
        return entity;
    }

    PurchasePriceResponse toResponse(PurchasePriceEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current(); return toResponse(entity, loadProducts(List.of(entity), audit), loadSuppliers(List.of(entity), audit));
    }

    private PurchasePriceEntity findBestMatch(Long companyId, Long accountBookId, Long supplierId, Long productId, LocalDate date) {
        if (supplierId != null) { PurchasePriceEntity supplierPrice = pickLatest(listEffective(companyId, accountBookId, supplierId, productId, date)); if (supplierPrice != null) return supplierPrice; }
        return pickLatest(listEffective(companyId, accountBookId, null, productId, date));
    }

    private List<PurchasePriceEntity> listEffective(Long companyId, Long accountBookId, Long supplierId, Long productId, LocalDate date) {
        LambdaQueryWrapper<PurchasePriceEntity> wrapper = new LambdaQueryWrapper<PurchasePriceEntity>().eq(PurchasePriceEntity::getCompanyId, companyId)
                .eq(PurchasePriceEntity::getAccountBookId, accountBookId).eq(PurchasePriceEntity::getProductId, productId).eq(PurchasePriceEntity::getStatus, STATUS_ACTIVE)
                .eq(PurchasePriceEntity::getDeletedFlag, 0).le(PurchasePriceEntity::getEffectiveFrom, date)
                .and(w -> w.isNull(PurchasePriceEntity::getEffectiveTo).or().ge(PurchasePriceEntity::getEffectiveTo, date));
        if (supplierId == null) wrapper.isNull(PurchasePriceEntity::getSupplierId); else wrapper.eq(PurchasePriceEntity::getSupplierId, supplierId);
        return purchasePriceMapper.selectList(wrapper);
    }

    private PurchasePriceEntity pickLatest(List<PurchasePriceEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream().sorted((a, b) -> { int byFrom = b.getEffectiveFrom().compareTo(a.getEffectiveFrom()); if (byFrom != 0) return byFrom; return Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId()); }).findFirst().orElse(null);
    }

    private Map<Long, ProductEntity> loadProducts(List<PurchasePriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(PurchasePriceEntity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet()); if (ids.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(ids).stream().filter(p -> Objects.equals(p.getCompanyId(), audit.companyId())).collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a, HashMap::new));
    }
    private Map<Long, SupplierEntity> loadSuppliers(List<PurchasePriceEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(PurchasePriceEntity::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet()); if (ids.isEmpty()) return Map.of();
        return supplierMapper.selectBatchIds(ids).stream().filter(s -> Objects.equals(s.getCompanyId(), audit.companyId())).collect(Collectors.toMap(SupplierEntity::getId, s -> s, (a, b) -> a, HashMap::new));
    }
    private PurchasePriceResponse toResponse(PurchasePriceEntity entity, Map<Long, ProductEntity> products, Map<Long, SupplierEntity> suppliers) {
        ProductEntity product = products.get(entity.getProductId()); SupplierEntity supplier = entity.getSupplierId() == null ? null : suppliers.get(entity.getSupplierId());
        return new PurchasePriceResponse(entity.getId(), entity.getSupplierId(), supplier == null ? null : supplier.getSupplierName(), entity.getProductId(),
                product == null ? null : product.getProductCode(), product == null ? null : product.getProductName(), ScalePrecision.amount(entity.getListPrice()),
                ScalePrecision.amount(entity.getMaxPrice()), entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getStatus(), entity.getRemark());
    }
    private String normalizeStatus(String status) { String upper = status.trim().toUpperCase(java.util.Locale.ROOT); if (!Set.of("ACTIVE", "INACTIVE").contains(upper)) throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE"); return upper; }
}

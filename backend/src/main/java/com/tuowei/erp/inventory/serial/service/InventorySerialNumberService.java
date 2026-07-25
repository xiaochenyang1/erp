package com.tuowei.erp.inventory.serial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.model.InventorySerialNumberEntity;
import com.tuowei.erp.inventory.serial.web.InventorySerialCreateRequest;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.inventory.serial.web.InventorySerialResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventorySerialNumberService {

    private static final String STATUS_IN_STOCK = "IN_STOCK";
    private static final String STATUS_ISSUED = "ISSUED";
    private static final String STATUS_SCRAPPED = "SCRAPPED";

    private final InventorySerialNumberMapper serialNumberMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public InventorySerialNumberService(
            InventorySerialNumberMapper serialNumberMapper,
            ProductMapper productMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.serialNumberMapper = serialNumberMapper;
        this.productMapper = productMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public InventorySerialResponse create(InventorySerialCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity product = requireSerialProduct(request.productId(), audit);
        String serialNo = normalizeSerial(request.serialNo());
        ensureUnique(audit, product.getId(), serialNo);
        LocalDateTime now = audit.now();
        InventorySerialNumberEntity entity = new InventorySerialNumberEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setProductId(product.getId());
        entity.setWarehouseId(request.warehouseId());
        entity.setLocationId(request.locationId());
        entity.setSerialNo(serialNo);
        entity.setStatus(STATUS_IN_STOCK);
        entity.setInboundBizType(trimToNull(request.inboundBizType()));
        entity.setInboundBizNo(trimToNull(request.inboundBizNo()));
        entity.setRemark(trimToNull(request.remark()));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        serialNumberMapper.insert(entity);
        return toResponse(entity, product);
    }

    @Transactional
    public InventorySerialResponse issue(Long id, String outboundBizType, String outboundBizNo) {
        return transition(id, STATUS_IN_STOCK, STATUS_ISSUED, outboundBizType, outboundBizNo);
    }

    @Transactional
    public InventorySerialResponse scrap(Long id) {
        return transition(id, STATUS_IN_STOCK, STATUS_SCRAPPED, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventorySerialResponse> list(InventorySerialPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventorySerialPageQuery safe = query == null ? new InventorySerialPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());
        LambdaQueryWrapper<InventorySerialNumberEntity> wrapper = new LambdaQueryWrapper<InventorySerialNumberEntity>()
                .eq(InventorySerialNumberEntity::getCompanyId, audit.companyId())
                .eq(InventorySerialNumberEntity::getAccountBookId, audit.accountBookId())
                .eq(InventorySerialNumberEntity::getDeletedFlag, 0)
                .orderByDesc(InventorySerialNumberEntity::getUpdatedTime)
                .orderByDesc(InventorySerialNumberEntity::getId);
        if (safe.getProductId() != null) wrapper.eq(InventorySerialNumberEntity::getProductId, safe.getProductId());
        if (safe.getWarehouseId() != null) wrapper.eq(InventorySerialNumberEntity::getWarehouseId, safe.getWarehouseId());
        if (StringUtils.hasText(safe.getStatus())) wrapper.eq(InventorySerialNumberEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(safe.getKeyword())) {
            String kw = safe.getKeyword().trim();
            wrapper.and(w -> w.like(InventorySerialNumberEntity::getSerialNo, kw)
                    .or().like(InventorySerialNumberEntity::getInboundBizNo, kw)
                    .or().like(InventorySerialNumberEntity::getOutboundBizNo, kw));
        }
        Page<InventorySerialNumberEntity> page = serialNumberMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, ProductEntity> products = loadProducts(page.getRecords(), audit);
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(e -> toResponse(e, products.get(e.getProductId()))).toList());
    }

    private InventorySerialResponse transition(Long id, String from, String to, String outboundBizType, String outboundBizNo) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventorySerialNumberEntity entity = requireSerial(id, audit);
        if (!from.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前序列号状态不允许该操作");
        }
        entity.setStatus(to);
        if (STATUS_ISSUED.equals(to)) {
            entity.setOutboundBizType(trimToNull(outboundBizType));
            entity.setOutboundBizNo(trimToNull(outboundBizNo));
        }
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(serialNumberMapper.updateById(entity), "序列号已被其他操作修改，请刷新后重试");
        return toResponse(entity, requireProduct(entity.getProductId(), audit));
    }

    private ProductEntity requireSerialProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = requireProduct(productId, audit);
        if (!Integer.valueOf(1).equals(product.getSerialControlled())) {
            throw new IllegalArgumentException("商品未启用序列号管理");
        }
        return product;
    }

    private ProductEntity requireProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !Objects.equals(product.getCompanyId(), audit.companyId())
                || !Objects.equals(product.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    private InventorySerialNumberEntity requireSerial(Long id, AuditMetadata audit) {
        InventorySerialNumberEntity entity = serialNumberMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("序列号不存在");
        }
        return entity;
    }

    private void ensureUnique(AuditMetadata audit, Long productId, String serialNo) {
        Long count = serialNumberMapper.selectCount(new LambdaQueryWrapper<InventorySerialNumberEntity>()
                .eq(InventorySerialNumberEntity::getCompanyId, audit.companyId())
                .eq(InventorySerialNumberEntity::getAccountBookId, audit.accountBookId())
                .eq(InventorySerialNumberEntity::getProductId, productId)
                .eq(InventorySerialNumberEntity::getSerialNo, serialNo)
                .eq(InventorySerialNumberEntity::getDeletedFlag, 0));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("序列号已存在");
        }
    }

    private Map<Long, ProductEntity> loadProducts(List<InventorySerialNumberEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(InventorySerialNumberEntity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return productMapper.selectBatchIds(ids).stream()
                .filter(p -> Objects.equals(p.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a, HashMap::new));
    }

    private InventorySerialResponse toResponse(InventorySerialNumberEntity entity, ProductEntity product) {
        return new InventorySerialResponse(
                entity.getId(),
                entity.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                entity.getWarehouseId(),
                entity.getLocationId(),
                entity.getSerialNo(),
                entity.getStatus(),
                entity.getInboundBizType(),
                entity.getInboundBizNo(),
                entity.getOutboundBizType(),
                entity.getOutboundBizNo(),
                entity.getRemark(),
                entity.getUpdatedTime()
        );
    }

    private String normalizeSerial(String serialNo) {
        if (!StringUtils.hasText(serialNo)) throw new IllegalArgumentException("序列号不能为空");
        return serialNo.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }
}

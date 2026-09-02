package com.tuowei.erp.inventory.serial.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.model.InventorySerialNumberEntity;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.inventory.serial.web.InventorySerialResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventorySerialNumberQueryService {

    private final InventorySerialNumberMapper serialNumberMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public InventorySerialNumberQueryService(
            InventorySerialNumberMapper serialNumberMapper,
            ProductMapper productMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.serialNumberMapper = serialNumberMapper;
        this.productMapper = productMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventorySerialResponse> list(InventorySerialPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventorySerialPageQuery safe = query == null ? new InventorySerialPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(
                safe.getPageNo() == null ? null : safe.getPageNo().intValue()
        );
        long pageSize = PageQueryNormalizer.normalizePageSize(
                safe.getPageSize() == null ? null : safe.getPageSize().intValue()
        );
        LambdaQueryWrapper<InventorySerialNumberEntity> wrapper = new LambdaQueryWrapper<InventorySerialNumberEntity>()
                .eq(InventorySerialNumberEntity::getCompanyId, audit.companyId())
                .eq(InventorySerialNumberEntity::getAccountBookId, audit.accountBookId())
                .eq(InventorySerialNumberEntity::getDeletedFlag, 0)
                .orderByDesc(InventorySerialNumberEntity::getUpdatedTime)
                .orderByDesc(InventorySerialNumberEntity::getId);
        if (safe.getProductId() != null) {
            wrapper.eq(InventorySerialNumberEntity::getProductId, safe.getProductId());
        }
        if (safe.getWarehouseId() != null) {
            wrapper.eq(InventorySerialNumberEntity::getWarehouseId, safe.getWarehouseId());
        }
        if (safe.getLocationId() != null) {
            wrapper.eq(InventorySerialNumberEntity::getLocationId, safe.getLocationId());
        }
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(InventorySerialNumberEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            String keyword = safe.getKeyword().trim();
            wrapper.and(w -> w.like(InventorySerialNumberEntity::getSerialNo, keyword)
                    .or().like(InventorySerialNumberEntity::getInboundBizNo, keyword)
                    .or().like(InventorySerialNumberEntity::getOutboundBizNo, keyword));
        }
        Page<InventorySerialNumberEntity> page = serialNumberMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper
        );
        Map<Long, ProductEntity> products = loadProducts(page.getRecords(), audit);
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(entity -> toResponse(entity, products.get(entity.getProductId()))).toList()
        );
    }

    ProductEntity requireSerialProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = requireProduct(productId, audit);
        if (!Integer.valueOf(1).equals(product.getSerialControlled())) {
            throw new IllegalArgumentException("商品未启用序列号管理");
        }
        return product;
    }

    ProductEntity requireProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !Objects.equals(product.getCompanyId(), audit.companyId())
                || !Objects.equals(product.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    InventorySerialNumberEntity requireSerial(Long id, AuditMetadata audit) {
        InventorySerialNumberEntity entity = serialNumberMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("序列号不存在");
        }
        return entity;
    }

    InventorySerialResponse toResponse(InventorySerialNumberEntity entity, ProductEntity product) {
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

    private Map<Long, ProductEntity> loadProducts(List<InventorySerialNumberEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream()
                .map(InventorySerialNumberEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(product -> Objects.equals(product.getCompanyId(), audit.companyId()))
                .filter(product -> Objects.equals(product.getAccountBookId(), audit.accountBookId()))
                .filter(product -> Objects.equals(product.getDeletedFlag(), 0))
                .collect(Collectors.toMap(ProductEntity::getId, product -> product, (a, b) -> a, HashMap::new));
    }
}

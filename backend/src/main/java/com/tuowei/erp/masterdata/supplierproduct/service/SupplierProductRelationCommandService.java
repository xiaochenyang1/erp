package com.tuowei.erp.masterdata.supplierproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplierproduct.mapper.SupplierProductRelationMapper;
import com.tuowei.erp.masterdata.supplierproduct.model.SupplierProductRelationEntity;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationRequest;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Write side of supplier-product relations: tenant-scoped upsert with single-default enforcement and soft delete. */
@Service
public class SupplierProductRelationCommandService {

    private final SupplierProductRelationMapper mapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory audit;
    private final SupplierProductRelationQueryService queryService;

    public SupplierProductRelationCommandService(
            SupplierProductRelationMapper mapper,
            ProductMapper productMapper,
            AuditMetadataFactory audit,
            SupplierProductRelationQueryService queryService
    ) {
        this.mapper = mapper;
        this.productMapper = productMapper;
        this.audit = audit;
        this.queryService = queryService;
    }

    @Transactional
    public SupplierProductRelationResponse save(Long supplierId, SupplierProductRelationRequest request) {
        AuditMetadata metadata = audit.current();
        queryService.requireSupplier(supplierId, metadata);
        requireProduct(request.productId(), metadata);

        SupplierProductRelationEntity entity = mapper.selectOne(new LambdaQueryWrapper<SupplierProductRelationEntity>()
                .eq(SupplierProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(SupplierProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(SupplierProductRelationEntity::getSupplierId, supplierId)
                .eq(SupplierProductRelationEntity::getProductId, request.productId()));
        if (entity == null) {
            entity = new SupplierProductRelationEntity();
            entity.setCompanyId(metadata.companyId());
            entity.setAccountBookId(metadata.accountBookId());
            entity.setSupplierId(supplierId);
            entity.setProductId(request.productId());
            entity.setCreatedBy(metadata.userId());
            entity.setCreatedTime(metadata.now());
            entity.setVersion(0);
        }
        if (Boolean.TRUE.equals(request.defaultSupplier())) {
            clearOtherDefaults(entity.getId(), request.productId(), metadata);
        }
        entity.setDeletedFlag(0);
        entity.setSupplierProductCode(trim(request.supplierProductCode()));
        entity.setSupplierProductName(trim(request.supplierProductName()));
        entity.setMinPurchaseQty(request.minPurchaseQty() == null ? BigDecimal.ZERO : request.minPurchaseQty());
        entity.setLeadTimeDays(request.leadTimeDays() == null ? 0 : request.leadTimeDays());
        entity.setDefaultSupplierFlag(Boolean.TRUE.equals(request.defaultSupplier()) ? 1 : 0);
        entity.setRemark(trim(request.remark()));
        entity.setStatus("ACTIVE");
        entity.setUpdatedBy(metadata.userId());
        entity.setUpdatedTime(metadata.now());
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return queryService.response(entity, metadata);
    }

    @Transactional
    public void delete(Long supplierId, Long id) {
        AuditMetadata metadata = audit.current();
        SupplierProductRelationEntity entity = require(id, metadata);
        if (!Objects.equals(entity.getSupplierId(), supplierId)) {
            throw new IllegalArgumentException("供应商商品关系不存在");
        }
        entity.setDeletedFlag(1);
        entity.setUpdatedBy(metadata.userId());
        entity.setUpdatedTime(metadata.now());
        mapper.updateById(entity);
    }

    private void clearOtherDefaults(Long currentId, Long productId, AuditMetadata metadata) {
        List<SupplierProductRelationEntity> defaults = mapper.selectList(new LambdaQueryWrapper<SupplierProductRelationEntity>()
                .eq(SupplierProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(SupplierProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(SupplierProductRelationEntity::getProductId, productId)
                .eq(SupplierProductRelationEntity::getDefaultSupplierFlag, 1)
                .eq(SupplierProductRelationEntity::getStatus, "ACTIVE")
                .eq(SupplierProductRelationEntity::getDeletedFlag, 0));
        for (SupplierProductRelationEntity other : defaults) {
            if (!Objects.equals(other.getId(), currentId)) {
                other.setDefaultSupplierFlag(0);
                other.setUpdatedBy(metadata.userId());
                other.setUpdatedTime(metadata.now());
                mapper.updateById(other);
            }
        }
    }

    private ProductEntity requireProduct(Long id, AuditMetadata metadata) {
        var entity = productMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("商品不存在");
        }
        return entity;
    }

    private SupplierProductRelationEntity require(Long id, AuditMetadata metadata) {
        var entity = mapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("供应商商品关系不存在");
        }
        return entity;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

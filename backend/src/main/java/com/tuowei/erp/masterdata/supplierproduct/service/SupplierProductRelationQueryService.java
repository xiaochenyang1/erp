package com.tuowei.erp.masterdata.supplierproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplierproduct.mapper.SupplierProductRelationMapper;
import com.tuowei.erp.masterdata.supplierproduct.model.SupplierProductRelationEntity;
import com.tuowei.erp.masterdata.supplierproduct.web.SupplierProductRelationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Read side of supplier-product relations: tenant-scoped listing, active-product enrichment and the non-transactional lookup reused by purchase ordering. */
@Service
public class SupplierProductRelationQueryService {

    private final SupplierProductRelationMapper mapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory audit;

    public SupplierProductRelationQueryService(
            SupplierProductRelationMapper mapper,
            SupplierMapper supplierMapper,
            ProductMapper productMapper,
            AuditMetadataFactory audit
    ) {
        this.mapper = mapper;
        this.supplierMapper = supplierMapper;
        this.productMapper = productMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SupplierProductRelationResponse> list(Long supplierId) {
        AuditMetadata metadata = audit.current();
        requireSupplier(supplierId, metadata);
        return mapper.selectList(new LambdaQueryWrapper<SupplierProductRelationEntity>()
                        .eq(SupplierProductRelationEntity::getCompanyId, metadata.companyId())
                        .eq(SupplierProductRelationEntity::getAccountBookId, metadata.accountBookId())
                        .eq(SupplierProductRelationEntity::getSupplierId, supplierId)
                        .eq(SupplierProductRelationEntity::getStatus, "ACTIVE")
                        .eq(SupplierProductRelationEntity::getDeletedFlag, 0)
                        .orderByDesc(SupplierProductRelationEntity::getDefaultSupplierFlag)
                        .orderByAsc(SupplierProductRelationEntity::getId))
                .stream()
                .map(entity -> response(entity, metadata))
                .toList();
    }

    public SupplierProductRelationEntity find(Long supplierId, Long productId, AuditMetadata metadata) {
        return mapper.selectOne(new LambdaQueryWrapper<SupplierProductRelationEntity>()
                .eq(SupplierProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(SupplierProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(SupplierProductRelationEntity::getSupplierId, supplierId)
                .eq(SupplierProductRelationEntity::getProductId, productId)
                .eq(SupplierProductRelationEntity::getStatus, "ACTIVE")
                .eq(SupplierProductRelationEntity::getDeletedFlag, 0));
    }

    SupplierProductRelationResponse response(SupplierProductRelationEntity entity, AuditMetadata metadata) {
        // A product can be disabled or soft-deleted after the relation is saved.
        // Keep the relation visible and leave product display fields empty.
        ProductEntity product = findActiveProduct(entity.getProductId(), metadata);
        return new SupplierProductRelationResponse(
                entity.getId(), entity.getSupplierId(), entity.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                entity.getSupplierProductCode(), entity.getSupplierProductName(),
                entity.getMinPurchaseQty(), entity.getLeadTimeDays(),
                Integer.valueOf(1).equals(entity.getDefaultSupplierFlag()),
                entity.getRemark(), entity.getStatus());
    }

    void requireSupplier(Long id, AuditMetadata metadata) {
        var entity = supplierMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("供应商不存在");
        }
    }

    private ProductEntity findActiveProduct(Long id, AuditMetadata metadata) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            return null;
        }
        return entity;
    }
}

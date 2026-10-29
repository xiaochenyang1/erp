package com.tuowei.erp.masterdata.customerproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customerproduct.mapper.CustomerProductRelationMapper;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Read side of customer-product relations: tenant-scoped listing, active-product enrichment and the non-transactional lookup reusable by downstream sales flows. */
@Service
public class CustomerProductRelationQueryService {

    private final CustomerProductRelationMapper mapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory audit;

    public CustomerProductRelationQueryService(
            CustomerProductRelationMapper mapper,
            CustomerMapper customerMapper,
            ProductMapper productMapper,
            AuditMetadataFactory audit
    ) {
        this.mapper = mapper;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CustomerProductRelationResponse> list(Long customerId) {
        AuditMetadata metadata = audit.current();
        requireCustomer(customerId, metadata);
        return mapper.selectList(new LambdaQueryWrapper<CustomerProductRelationEntity>()
                        .eq(CustomerProductRelationEntity::getCompanyId, metadata.companyId())
                        .eq(CustomerProductRelationEntity::getAccountBookId, metadata.accountBookId())
                        .eq(CustomerProductRelationEntity::getCustomerId, customerId)
                        .eq(CustomerProductRelationEntity::getStatus, "ACTIVE")
                        .eq(CustomerProductRelationEntity::getDeletedFlag, 0)
                        .orderByAsc(CustomerProductRelationEntity::getId))
                .stream()
                .map(entity -> response(entity, metadata))
                .toList();
    }

    public CustomerProductRelationEntity find(Long customerId, Long productId, AuditMetadata metadata) {
        return mapper.selectOne(new LambdaQueryWrapper<CustomerProductRelationEntity>()
                .eq(CustomerProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(CustomerProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(CustomerProductRelationEntity::getCustomerId, customerId)
                .eq(CustomerProductRelationEntity::getProductId, productId)
                .eq(CustomerProductRelationEntity::getStatus, "ACTIVE")
                .eq(CustomerProductRelationEntity::getDeletedFlag, 0));
    }

    CustomerProductRelationResponse response(CustomerProductRelationEntity entity, AuditMetadata metadata) {
        // A product can be disabled or soft-deleted after the relation is saved.
        // Keep the relation visible and leave product display fields empty.
        ProductEntity product = findActiveProduct(entity.getProductId(), metadata);
        return new CustomerProductRelationResponse(
                entity.getId(), entity.getCustomerId(), entity.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                entity.getCustomerProductCode(), entity.getCustomerProductName(),
                entity.getDeliveryPreference(), entity.getPackagingPreference(),
                entity.getRemark(), entity.getStatus());
    }

    void requireCustomer(Long id, AuditMetadata metadata) {
        var entity = customerMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("客户不存在");
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

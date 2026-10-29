package com.tuowei.erp.masterdata.customerproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customerproduct.mapper.CustomerProductRelationMapper;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationRequest;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/** Write side of customer-product relations: tenant-scoped upsert and soft delete. */
@Service
public class CustomerProductRelationCommandService {

    private final CustomerProductRelationMapper mapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory audit;
    private final CustomerProductRelationQueryService queryService;

    public CustomerProductRelationCommandService(
            CustomerProductRelationMapper mapper,
            ProductMapper productMapper,
            AuditMetadataFactory audit,
            CustomerProductRelationQueryService queryService
    ) {
        this.mapper = mapper;
        this.productMapper = productMapper;
        this.audit = audit;
        this.queryService = queryService;
    }

    @Transactional
    public CustomerProductRelationResponse save(Long customerId, CustomerProductRelationRequest request) {
        AuditMetadata metadata = audit.current();
        queryService.requireCustomer(customerId, metadata);
        requireProduct(request.productId(), metadata);

        CustomerProductRelationEntity entity = mapper.selectOne(new LambdaQueryWrapper<CustomerProductRelationEntity>()
                .eq(CustomerProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(CustomerProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(CustomerProductRelationEntity::getCustomerId, customerId)
                .eq(CustomerProductRelationEntity::getProductId, request.productId()));
        if (entity == null) {
            entity = new CustomerProductRelationEntity();
            entity.setCompanyId(metadata.companyId());
            entity.setAccountBookId(metadata.accountBookId());
            entity.setCustomerId(customerId);
            entity.setProductId(request.productId());
            entity.setCreatedBy(metadata.userId());
            entity.setCreatedTime(metadata.now());
            entity.setVersion(0);
        }
        entity.setDeletedFlag(0);
        entity.setCustomerProductCode(trim(request.customerProductCode()));
        entity.setCustomerProductName(trim(request.customerProductName()));
        entity.setDeliveryPreference(trim(request.deliveryPreference()));
        entity.setPackagingPreference(trim(request.packagingPreference()));
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
    public void delete(Long customerId, Long id) {
        AuditMetadata metadata = audit.current();
        CustomerProductRelationEntity entity = require(id, metadata);
        if (!Objects.equals(entity.getCustomerId(), customerId)) {
            throw new IllegalArgumentException("客户商品关系不存在");
        }
        entity.setDeletedFlag(1);
        entity.setUpdatedBy(metadata.userId());
        entity.setUpdatedTime(metadata.now());
        mapper.updateById(entity);
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

    private CustomerProductRelationEntity require(Long id, AuditMetadata metadata) {
        var entity = mapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("客户商品关系不存在");
        }
        return entity;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

package com.tuowei.erp.masterdata.customerproduct.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customerproduct.mapper.CustomerProductRelationMapper;
import com.tuowei.erp.masterdata.customerproduct.model.CustomerProductRelationEntity;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationRequest;
import com.tuowei.erp.masterdata.customerproduct.web.CustomerProductRelationResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class CustomerProductRelationService {

    private final CustomerProductRelationMapper mapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory audit;

    public CustomerProductRelationService(
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

    @Transactional
    public CustomerProductRelationResponse save(Long customerId, CustomerProductRelationRequest request) {
        AuditMetadata metadata = audit.current();
        requireCustomer(customerId, metadata);
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
        return response(entity, metadata);
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

    public CustomerProductRelationEntity find(Long customerId, Long productId, AuditMetadata metadata) {
        return mapper.selectOne(new LambdaQueryWrapper<CustomerProductRelationEntity>()
                .eq(CustomerProductRelationEntity::getCompanyId, metadata.companyId())
                .eq(CustomerProductRelationEntity::getAccountBookId, metadata.accountBookId())
                .eq(CustomerProductRelationEntity::getCustomerId, customerId)
                .eq(CustomerProductRelationEntity::getProductId, productId)
                .eq(CustomerProductRelationEntity::getStatus, "ACTIVE")
                .eq(CustomerProductRelationEntity::getDeletedFlag, 0));
    }

    private CustomerProductRelationResponse response(CustomerProductRelationEntity entity, AuditMetadata metadata) {
        ProductEntity product = findActiveProduct(entity.getProductId(), metadata);
        return new CustomerProductRelationResponse(
                entity.getId(), entity.getCustomerId(), entity.getProductId(),
                product.getProductCode(), product.getProductName(),
                entity.getCustomerProductCode(), entity.getCustomerProductName(),
                entity.getDeliveryPreference(), entity.getPackagingPreference(),
                entity.getRemark(), entity.getStatus());
    }

    private ProductEntity findActiveProduct(Long id, AuditMetadata metadata) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            return null;
        }
        return entity;
    }

    private void requireCustomer(Long id, AuditMetadata metadata) {
        var entity = customerMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), metadata.companyId())
                || !Objects.equals(entity.getAccountBookId(), metadata.accountBookId())
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("客户不存在");
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

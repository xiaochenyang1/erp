package com.tuowei.erp.masterdata.product.service;

import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductValidator {

    private final ProductMapper productMapper;

    public ProductValidator(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public ProductEntity requireProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(product.getStatus())
                || !Objects.equals(product.getCompanyId(), companyId)
                || !Objects.equals(product.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("商品不存在或已停用");
        }
        return product;
    }

    public Map<Long, ProductEntity> requireProducts(Collection<Long> productIds, Long companyId, Long accountBookId) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ProductEntity> products = productMapper.selectBatchIds(productIds)
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        for (Long id : productIds) {
            ProductEntity product = products.get(id);
            if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                    || !"ACTIVE".equalsIgnoreCase(product.getStatus())
                    || !Objects.equals(product.getCompanyId(), companyId)
                    || !Objects.equals(product.getAccountBookId(), accountBookId)) {
                throw new IllegalArgumentException("商品不存在或已停用");
            }
        }
        return products;
    }
}

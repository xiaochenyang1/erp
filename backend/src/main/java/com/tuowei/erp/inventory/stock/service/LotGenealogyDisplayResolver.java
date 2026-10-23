package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LotGenealogyDisplayResolver {

    public record ProductDisplay(String code, String name) {
    }

    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    public LotGenealogyDisplayResolver(ProductMapper productMapper, WarehouseMapper warehouseMapper) {
        this.productMapper = productMapper;
        this.warehouseMapper = warehouseMapper;
    }

    public Map<Long, ProductDisplay> products(
            Collection<Long> productIds,
            Long companyId,
            Long accountBookId
    ) {
        Set<Long> ids = ids(productIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<ProductEntity>()
                        .eq(ProductEntity::getCompanyId, companyId)
                        .eq(ProductEntity::getAccountBookId, accountBookId)
                        .in(ProductEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(
                        ProductEntity::getId,
                        entity -> new ProductDisplay(entity.getProductCode(), entity.getProductName()),
                        (left, right) -> left
                ));
    }

    public Map<Long, String> warehouseNames(
            Collection<Long> warehouseIds,
            Long companyId,
            Long accountBookId
    ) {
        Set<Long> ids = ids(warehouseIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectList(new LambdaQueryWrapper<WarehouseEntity>()
                        .eq(WarehouseEntity::getCompanyId, companyId)
                        .eq(WarehouseEntity::getAccountBookId, accountBookId)
                        .in(WarehouseEntity::getId, ids))
                .stream()
                .collect(Collectors.toMap(
                        WarehouseEntity::getId,
                        WarehouseEntity::getWarehouseName,
                        (left, right) -> left
                ));
    }

    private static Set<Long> ids(Collection<Long> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }
}

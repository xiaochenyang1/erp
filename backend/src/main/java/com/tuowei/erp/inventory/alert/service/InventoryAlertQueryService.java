package com.tuowei.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertDispositionEntity;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Read-side rule filtering, stock hydration, disposition overlay and response mapping for inventory alerts. */
@Service
public class InventoryAlertQueryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final InventoryAlertRuleMapper alertRuleMapper;
    private final InventoryAlertDispositionMapper dispositionMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;

    public InventoryAlertQueryService(
            InventoryAlertRuleMapper alertRuleMapper,
            InventoryAlertDispositionMapper dispositionMapper,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            InventoryBalanceMapper inventoryBalanceMapper
    ) {
        this.alertRuleMapper = alertRuleMapper;
        this.dispositionMapper = dispositionMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
    }

    @Transactional(readOnly = true)
    public List<InventoryAlertRuleResponse> listRules(Long warehouseId, Long productId, Boolean enabled) {
        AuditMetadata audit = auditMetadataFactory.current();
        LambdaQueryWrapper<InventoryAlertRuleEntity> wrapper = new LambdaQueryWrapper<InventoryAlertRuleEntity>()
                .eq(InventoryAlertRuleEntity::getCompanyId, audit.companyId())
                .eq(InventoryAlertRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryAlertRuleEntity::getDeletedFlag, 0)
                .orderByDesc(InventoryAlertRuleEntity::getUpdatedTime)
                .orderByDesc(InventoryAlertRuleEntity::getId);
        if (warehouseId != null) {
            wrapper.eq(InventoryAlertRuleEntity::getWarehouseId, warehouseId);
        }
        if (productId != null) {
            wrapper.eq(InventoryAlertRuleEntity::getProductId, productId);
        }
        if (enabled != null) {
            wrapper.eq(InventoryAlertRuleEntity::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0);
        }
        List<InventoryAlertRuleEntity> rules = alertRuleMapper.selectList(wrapper);
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<Long, WarehouseEntity> warehouses = loadWarehouses(rules, audit);
        Map<Long, ProductEntity> products = loadProducts(rules, audit);
        return rules.stream()
                .map(rule -> toRuleResponse(
                        rule,
                        warehouses.get(rule.getWarehouseId()),
                        products.get(rule.getProductId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(Long warehouseId, Long productId) {
        return listLowStock(warehouseId, productId, auditMetadataFactory.current());
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(
            Long warehouseId,
            Long productId,
            AuditMetadata audit
    ) {
        LambdaQueryWrapper<InventoryAlertRuleEntity> wrapper = new LambdaQueryWrapper<InventoryAlertRuleEntity>()
                .eq(InventoryAlertRuleEntity::getCompanyId, audit.companyId())
                .eq(InventoryAlertRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryAlertRuleEntity::getDeletedFlag, 0)
                .eq(InventoryAlertRuleEntity::getEnabled, 1);
        if (warehouseId != null) {
            wrapper.eq(InventoryAlertRuleEntity::getWarehouseId, warehouseId);
        }
        if (productId != null) {
            wrapper.eq(InventoryAlertRuleEntity::getProductId, productId);
        }

        Map<String, InventoryAlertDispositionEntity> dispositions = loadDispositions(audit);
        List<InventoryAlertRuleEntity> rules = alertRuleMapper.selectList(wrapper);
        if (rules.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> qtyOnHandByKey = loadQtyOnHand(rules, audit);
        Map<Long, WarehouseEntity> warehouses = loadWarehouses(rules, audit);
        Map<Long, ProductEntity> products = loadProducts(rules, audit);
        return rules.stream()
                .map(rule -> toLowStockResponse(
                        rule,
                        qtyOnHandByKey.getOrDefault(
                                alertKey(rule.getWarehouseId(), rule.getProductId()),
                                ScalePrecision.quantity(BigDecimal.ZERO)
                        ),
                        dispositions.get(alertKey(rule.getWarehouseId(), rule.getProductId())),
                        warehouses.get(rule.getWarehouseId()),
                        products.get(rule.getProductId())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    public InventoryAlertRuleResponse toRuleResponse(
            InventoryAlertRuleEntity rule,
            WarehouseEntity warehouse,
            ProductEntity product
    ) {
        return new InventoryAlertRuleResponse(
                rule.getId(),
                rule.getWarehouseId(),
                warehouse == null ? null : warehouse.getWarehouseName(),
                rule.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                rule.getMinQty(),
                Integer.valueOf(1).equals(rule.getEnabled()),
                rule.getRemark(),
                rule.getUpdatedTime()
        );
    }

    private InventoryLowStockResponse toLowStockResponse(
            InventoryAlertRuleEntity rule,
            BigDecimal qtyOnHand,
            InventoryAlertDispositionEntity disposition,
            WarehouseEntity warehouse,
            ProductEntity product
    ) {
        if (qtyOnHand.compareTo(rule.getMinQty()) >= 0) {
            return null;
        }
        BigDecimal shortageQty = ScalePrecision.quantity(rule.getMinQty().subtract(qtyOnHand));
        return new InventoryLowStockResponse(
                rule.getId(),
                rule.getId(),
                rule.getWarehouseId(),
                warehouse == null ? null : warehouse.getWarehouseName(),
                rule.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                qtyOnHand,
                qtyOnHand,
                rule.getMinQty(),
                rule.getMinQty(),
                shortageQty,
                null,
                "LOW_STOCK",
                rule.getUpdatedTime() == null ? rule.getCreatedTime() : rule.getUpdatedTime(),
                resolveStatus(disposition, shortageQty),
                rule.getRemark()
        );
    }

    private Map<String, BigDecimal> loadQtyOnHand(
            List<InventoryAlertRuleEntity> rules,
            AuditMetadata audit
    ) {
        List<Long> warehouseIds = rules.stream()
                .map(InventoryAlertRuleEntity::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> productIds = rules.stream()
                .map(InventoryAlertRuleEntity::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (warehouseIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new HashMap<>();
        inventoryBalanceMapper.selectList(new LambdaQueryWrapper<InventoryBalanceEntity>()
                        .eq(InventoryBalanceEntity::getCompanyId, audit.companyId())
                        .eq(InventoryBalanceEntity::getAccountBookId, audit.accountBookId())
                        .in(InventoryBalanceEntity::getWarehouseId, warehouseIds)
                        .in(InventoryBalanceEntity::getProductId, productIds))
                .forEach(balance -> map.put(
                        alertKey(balance.getWarehouseId(), balance.getProductId()),
                        ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()))
                ));
        return map;
    }

    private Map<Long, WarehouseEntity> loadWarehouses(
            List<InventoryAlertRuleEntity> rules,
            AuditMetadata audit
    ) {
        List<Long> ids = rules.stream()
                .map(InventoryAlertRuleEntity::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(WarehouseEntity::getId, entity -> entity, (left, right) -> left));
    }

    private Map<Long, ProductEntity> loadProducts(
            List<InventoryAlertRuleEntity> rules,
            AuditMetadata audit
    ) {
        List<Long> ids = rules.stream()
                .map(InventoryAlertRuleEntity::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(ProductEntity::getId, entity -> entity, (left, right) -> left));
    }

    private Map<String, InventoryAlertDispositionEntity> loadDispositions(AuditMetadata audit) {
        Map<String, InventoryAlertDispositionEntity> map = new HashMap<>();
        dispositionMapper.selectList(new LambdaQueryWrapper<InventoryAlertDispositionEntity>()
                        .eq(InventoryAlertDispositionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAlertDispositionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryAlertDispositionEntity::getDeletedFlag, 0))
                .forEach(disposition -> map.put(
                        alertKey(disposition.getWarehouseId(), disposition.getProductId()),
                        disposition
                ));
        return map;
    }

    private String resolveStatus(
            InventoryAlertDispositionEntity disposition,
            BigDecimal currentShortageQty
    ) {
        if (disposition == null) {
            return STATUS_ACTIVE;
        }
        BigDecimal snapshot = disposition.getSnapshotShortageQty();
        if (snapshot != null && currentShortageQty.compareTo(snapshot) > 0) {
            return STATUS_ACTIVE;
        }
        return disposition.getStatus();
    }

    private String alertKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }
}

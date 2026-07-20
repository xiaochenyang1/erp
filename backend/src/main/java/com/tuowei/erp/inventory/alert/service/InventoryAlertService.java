package com.tuowei.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertDispositionEntity;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryAlertService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_IGNORED = "IGNORED";
    private static final String STATUS_RESOLVED = "RESOLVED";

    private final InventoryAlertRuleMapper alertRuleMapper;
    private final InventoryAlertDispositionMapper dispositionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;

    public InventoryAlertService(
            InventoryAlertRuleMapper alertRuleMapper,
            InventoryAlertDispositionMapper dispositionMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            InventoryBalanceMapper inventoryBalanceMapper
    ) {
        this.alertRuleMapper = alertRuleMapper;
        this.dispositionMapper = dispositionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
    }

    @Transactional
    public InventoryAlertRuleResponse createRule(InventoryAlertRuleCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        requireProduct(request.productId(), audit.companyId(), audit.accountBookId());
        InventoryAlertRuleEntity rule = new InventoryAlertRuleEntity();
        rule.setCompanyId(audit.companyId());
        rule.setAccountBookId(audit.accountBookId());
        rule.setWarehouseId(request.warehouseId());
        rule.setProductId(request.productId());
        rule.setMinQty(ScalePrecision.quantity(request.minQty()));
        rule.setEnabled(1);
        rule.setDeletedFlag(0);
        rule.setRemark(request.remark());
        rule.setCreatedBy(audit.userId());
        rule.setCreatedTime(now);
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(now);
        rule.setVersion(0);
        alertRuleMapper.insert(rule);
        return toRuleResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(Long warehouseId, Long productId) {
        AuditMetadata audit = auditMetadataFactory.current();
        return listLowStock(warehouseId, productId, audit);
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(Long warehouseId, Long productId, AuditMetadata audit) {
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

        // 批量预取规则涉及的库存余额、仓库、商品，避免循环内逐行查询造成的 N+1
        Map<String, BigDecimal> qtyOnHandByKey = loadQtyOnHand(rules, audit);
        Map<Long, WarehouseEntity> warehouses = loadWarehouses(rules, audit);
        Map<Long, ProductEntity> products = loadProducts(rules, audit);

        return rules.stream()
                .map(rule -> {
                    BigDecimal qtyOnHand = qtyOnHandByKey.getOrDefault(
                            balanceKey(rule.getWarehouseId(), rule.getProductId()),
                            ScalePrecision.quantity(BigDecimal.ZERO));
                    if (qtyOnHand.compareTo(rule.getMinQty()) >= 0) {
                        return null;
                    }
                    BigDecimal shortageQty = ScalePrecision.quantity(rule.getMinQty().subtract(qtyOnHand));
                    String status = resolveStatus(
                            dispositions.get(dispositionKey(rule.getWarehouseId(), rule.getProductId())),
                            shortageQty
                    );
                    WarehouseEntity warehouse = warehouses.get(rule.getWarehouseId());
                    ProductEntity product = products.get(rule.getProductId());
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
                            status,
                            rule.getRemark()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String balanceKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }

    /**
     * 批量预取规则涉及的库存余额，按“仓库:商品”键返回现存量；缺行按 0 处理，语义与逐条 getQtyOnHand 一致。
     */
    private Map<String, BigDecimal> loadQtyOnHand(List<InventoryAlertRuleEntity> rules, AuditMetadata audit) {
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
                        balanceKey(balance.getWarehouseId(), balance.getProductId()),
                        ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()))));
        return map;
    }

    private Map<Long, WarehouseEntity> loadWarehouses(List<InventoryAlertRuleEntity> rules, AuditMetadata audit) {
        List<Long> ids = rules.stream()
                .map(InventoryAlertRuleEntity::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .filter(w -> Objects.equals(w.getCompanyId(), audit.companyId())
                        && Objects.equals(w.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(WarehouseEntity::getId, w -> w, (a, b) -> a));
    }

    private Map<Long, ProductEntity> loadProducts(List<InventoryAlertRuleEntity> rules, AuditMetadata audit) {
        List<Long> ids = rules.stream()
                .map(InventoryAlertRuleEntity::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(p -> Objects.equals(p.getCompanyId(), audit.companyId())
                        && Objects.equals(p.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a));
    }

    /**
     * 处置某仓某商品的当前低库存命中（忽略/已处理）。命中项本身是动态派生的，
     * 处置写入 inv_alert_disposition 并快照当前缺口，供 {@link #listLowStock} 叠加展示。
     */
    @Transactional
    public void handle(Long warehouseId, Long productId, String status, String remark) {
        AuditMetadata audit = auditMetadataFactory.current();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUS_IGNORED.equals(normalizedStatus) && !STATUS_RESOLVED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("处置状态只能为 IGNORED 或 RESOLVED");
        }
        // 必须存在一条启用规则命中该（仓+商品），否则处置无意义
        InventoryAlertRuleEntity rule = alertRuleMapper.selectOne(new LambdaQueryWrapper<InventoryAlertRuleEntity>()
                .eq(InventoryAlertRuleEntity::getCompanyId, audit.companyId())
                .eq(InventoryAlertRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryAlertRuleEntity::getWarehouseId, warehouseId)
                .eq(InventoryAlertRuleEntity::getProductId, productId)
                .eq(InventoryAlertRuleEntity::getDeletedFlag, 0)
                .eq(InventoryAlertRuleEntity::getEnabled, 1)
                .last("limit 1"));
        if (rule == null) {
            throw new IllegalArgumentException("低库存规则不存在或已停用");
        }
        BigDecimal qtyOnHand = inventoryPostingService.getQtyOnHand(
                warehouseId, productId, audit.companyId(), audit.accountBookId());
        if (qtyOnHand.compareTo(rule.getMinQty()) >= 0) {
            throw new IllegalArgumentException("当前库存已高于安全库存，无需处置");
        }
        BigDecimal shortageQty = ScalePrecision.quantity(rule.getMinQty().subtract(qtyOnHand));

        LocalDateTime now = audit.now();
        InventoryAlertDispositionEntity existing = dispositionMapper.selectOne(
                new LambdaQueryWrapper<InventoryAlertDispositionEntity>()
                        .eq(InventoryAlertDispositionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAlertDispositionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryAlertDispositionEntity::getWarehouseId, warehouseId)
                        .eq(InventoryAlertDispositionEntity::getProductId, productId)
                        .eq(InventoryAlertDispositionEntity::getDeletedFlag, 0)
                        .last("limit 1"));
        if (existing == null) {
            InventoryAlertDispositionEntity entity = new InventoryAlertDispositionEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setRuleId(rule.getId());
            entity.setWarehouseId(warehouseId);
            entity.setProductId(productId);
            entity.setStatus(normalizedStatus);
            entity.setSnapshotShortageQty(shortageQty);
            entity.setHandleRemark(remark);
            entity.setHandledBy(audit.userId());
            entity.setHandledTime(now);
            entity.setDeletedFlag(0);
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            dispositionMapper.insert(entity);
        } else {
            existing.setRuleId(rule.getId());
            existing.setStatus(normalizedStatus);
            existing.setSnapshotShortageQty(shortageQty);
            existing.setHandleRemark(remark);
            existing.setHandledBy(audit.userId());
            existing.setHandledTime(now);
            existing.setUpdatedBy(audit.userId());
            existing.setUpdatedTime(now);
            dispositionMapper.updateById(existing);
        }
    }

    @Transactional
    public void reactivate(Long warehouseId, Long productId) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAlertDispositionEntity existing = dispositionMapper.selectOne(
                new LambdaQueryWrapper<InventoryAlertDispositionEntity>()
                        .eq(InventoryAlertDispositionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAlertDispositionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryAlertDispositionEntity::getWarehouseId, warehouseId)
                        .eq(InventoryAlertDispositionEntity::getProductId, productId)
                        .eq(InventoryAlertDispositionEntity::getDeletedFlag, 0)
                        .last("limit 1"));
        if (existing == null) {
            return;
        }
        existing.setDeletedFlag(1);
        existing.setUpdatedBy(audit.userId());
        existing.setUpdatedTime(audit.now());
        dispositionMapper.updateById(existing);
    }

    private Map<String, InventoryAlertDispositionEntity> loadDispositions(AuditMetadata audit) {
        Map<String, InventoryAlertDispositionEntity> map = new HashMap<>();
        dispositionMapper.selectList(new LambdaQueryWrapper<InventoryAlertDispositionEntity>()
                        .eq(InventoryAlertDispositionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAlertDispositionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryAlertDispositionEntity::getDeletedFlag, 0))
                .forEach(d -> map.put(dispositionKey(d.getWarehouseId(), d.getProductId()), d));
        return map;
    }

    private String dispositionKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }

    /**
     * 叠加处置状态：若缺口比处置时快照更严重（当前缺口 &gt; 快照缺口），处置自动失效回到 ACTIVE，
     * 避免忽略一次后即使持续恶化也永久静默。
     */
    private String resolveStatus(InventoryAlertDispositionEntity disposition, BigDecimal currentShortageQty) {
        if (disposition == null) {
            return STATUS_ACTIVE;
        }
        BigDecimal snapshot = disposition.getSnapshotShortageQty();
        if (snapshot != null && currentShortageQty.compareTo(snapshot) > 0) {
            return STATUS_ACTIVE;
        }
        return disposition.getStatus();
    }

    private InventoryAlertRuleResponse toRuleResponse(InventoryAlertRuleEntity rule) {
        return new InventoryAlertRuleResponse(
                rule.getId(),
                rule.getWarehouseId(),
                rule.getProductId(),
                rule.getMinQty(),
                Integer.valueOf(1).equals(rule.getEnabled()),
                rule.getRemark()
        );
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private ProductEntity requireProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(product.getStatus())
                || !Objects.equals(product.getCompanyId(), companyId)
                || !Objects.equals(product.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("商品不存在或已停用");
        }
        return product;
    }
}

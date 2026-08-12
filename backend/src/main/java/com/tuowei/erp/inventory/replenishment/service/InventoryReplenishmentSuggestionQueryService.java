package com.tuowei.erp.inventory.replenishment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.model.InventoryReplenishmentSuggestionEntity;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 补货建议读侧：列表筛选与分页归一化、租户边界工单加载、展示主数据水合与响应映射。
 *
 * 写侧（{@link InventoryReplenishmentSuggestionPostingService}）在更新/取消/转单前通过
 * {@link #requireSuggestion} 取建议、通过 {@link #toResponse} 组装响应，保证读路径单点收敛。
 */
@Service
public class InventoryReplenishmentSuggestionQueryService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_CONVERTED = "CONVERTED";

    private final InventoryReplenishmentSuggestionMapper suggestionMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public InventoryReplenishmentSuggestionQueryService(
            InventoryReplenishmentSuggestionMapper suggestionMapper,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            SupplierMapper supplierMapper,
            PurchaseOrderMapper purchaseOrderMapper
    ) {
        this.suggestionMapper = suggestionMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.supplierMapper = supplierMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReplenishmentSuggestionResponse> list(InventoryReplenishmentSuggestionPageQuery query) {
        InventoryReplenishmentSuggestionPageQuery safeQuery =
                query == null ? new InventoryReplenishmentSuggestionPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<InventoryReplenishmentSuggestionEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );

        LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity> wrapper =
                new LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity>()
                        .eq(InventoryReplenishmentSuggestionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryReplenishmentSuggestionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryReplenishmentSuggestionEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(safeQuery.getSuggestionNo())) {
            wrapper.like(InventoryReplenishmentSuggestionEntity::getSuggestionNo, safeQuery.getSuggestionNo().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(InventoryReplenishmentSuggestionEntity::getStatus, normalizeStatus(safeQuery.getStatus()));
        }
        if (safeQuery.getWarehouseId() != null) {
            wrapper.eq(InventoryReplenishmentSuggestionEntity::getWarehouseId, safeQuery.getWarehouseId());
        }
        if (safeQuery.getProductId() != null) {
            wrapper.eq(InventoryReplenishmentSuggestionEntity::getProductId, safeQuery.getProductId());
        }
        if (safeQuery.getSupplierId() != null) {
            wrapper.eq(InventoryReplenishmentSuggestionEntity::getSupplierId, safeQuery.getSupplierId());
        }
        if (safeQuery.getCreatedTimeFrom() != null) {
            wrapper.ge(InventoryReplenishmentSuggestionEntity::getCreatedTime, safeQuery.getCreatedTimeFrom());
        }
        if (safeQuery.getCreatedTimeTo() != null) {
            wrapper.le(InventoryReplenishmentSuggestionEntity::getCreatedTime, safeQuery.getCreatedTimeTo());
        }
        wrapper.orderByDesc(InventoryReplenishmentSuggestionEntity::getId);

        Page<InventoryReplenishmentSuggestionEntity> result = suggestionMapper.selectPage(page, wrapper);
        DisplayMaps displayMaps = loadDisplayMaps(result.getRecords(), audit);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(
                                entity,
                                displayMaps.warehouses().get(entity.getWarehouseId()),
                                displayMaps.products().get(entity.getProductId()),
                                entity.getSupplierId() == null ? null : displayMaps.suppliers().get(entity.getSupplierId()),
                                entity.getPurchaseOrderId() == null ? null : displayMaps.purchaseOrders().get(entity.getPurchaseOrderId())
                        ))
                        .toList()
        );
    }

    /**
     * 按主键加载并校验租户边界（公司 + 账套 + 未删除）。写侧更新/取消/转单前都通过它取建议。
     */
    public InventoryReplenishmentSuggestionEntity requireSuggestion(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryReplenishmentSuggestionEntity entity = suggestionMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("补货建议不存在");
        }
        return entity;
    }

    /**
     * 组装建议响应（按主数据回填展示字段）。写侧更新/取消/转单后传入刚持久化的实体，复用同一映射逻辑。
     */
    public InventoryReplenishmentSuggestionResponse toResponse(InventoryReplenishmentSuggestionEntity entity) {
        return toResponse(
                entity,
                findTenantWarehouse(entity.getWarehouseId(), entity.getCompanyId(), entity.getAccountBookId()),
                findTenantProduct(entity.getProductId(), entity.getCompanyId(), entity.getAccountBookId()),
                entity.getSupplierId() == null
                        ? null
                        : findTenantSupplier(entity.getSupplierId(), entity.getCompanyId(), entity.getAccountBookId()),
                entity.getPurchaseOrderId() == null
                        ? null
                        : findTenantPurchaseOrder(entity.getPurchaseOrderId(), entity.getCompanyId(), entity.getAccountBookId())
        );
    }

    /**
     * 组装建议响应（使用已加载的展示主数据）。写侧 create 传入刚校验过的仓库/商品/供应商时直接复用。
     */
    public InventoryReplenishmentSuggestionResponse toResponse(
            InventoryReplenishmentSuggestionEntity entity,
            WarehouseEntity warehouse,
            ProductEntity product,
            SupplierEntity supplier,
            PurchaseOrderEntity purchaseOrder
    ) {
        return new InventoryReplenishmentSuggestionResponse(
                entity.getId(),
                entity.getSuggestionNo(),
                entity.getSourceType(),
                entity.getSourceRuleId(),
                entity.getWarehouseId(),
                warehouse == null ? null : warehouse.getWarehouseName(),
                entity.getProductId(),
                product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(),
                entity.getSupplierId(),
                supplier == null ? null : supplier.getSupplierName(),
                entity.getSuggestedQty(),
                entity.getShortageQtySnapshot(),
                entity.getExpectedArrivalDate(),
                entity.getStatus(),
                resolveFulfillmentStatus(entity, purchaseOrder),
                entity.getPurchaseOrderId(),
                entity.getPurchaseOrderNo(),
                entity.getRemark(),
                entity.getCreatedTime()
        );
    }

    private String resolveFulfillmentStatus(
            InventoryReplenishmentSuggestionEntity entity,
            PurchaseOrderEntity purchaseOrder
    ) {
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            return "CANCELLED";
        }
        if (STATUS_DRAFT.equals(entity.getStatus())) {
            return "SUGGESTED";
        }
        if (!STATUS_CONVERTED.equals(entity.getStatus())) {
            return entity.getStatus();
        }
        if (purchaseOrder == null) {
            return "PURCHASE_CREATED";
        }
        if ("RECEIVED".equals(purchaseOrder.getReceiptStatus())) {
            return "REPLENISHED";
        }
        if ("PARTIAL_RECEIVED".equals(purchaseOrder.getReceiptStatus())) {
            return "PARTIAL_RECEIVED";
        }
        if ("CANCELLED".equals(purchaseOrder.getStatus()) || "CLOSED".equals(purchaseOrder.getStatus())) {
            return "PURCHASE_CLOSED";
        }
        return "PURCHASE_CREATED";
    }

    private WarehouseEntity findTenantWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            return null;
        }
        return warehouse;
    }

    private ProductEntity findTenantProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null
                || !Objects.equals(product.getCompanyId(), companyId)
                || !Objects.equals(product.getAccountBookId(), accountBookId)) {
            return null;
        }
        return product;
    }

    private SupplierEntity findTenantSupplier(Long id, Long companyId, Long accountBookId) {
        SupplierEntity supplier = supplierMapper.selectById(id);
        if (supplier == null
                || !Objects.equals(supplier.getCompanyId(), companyId)
                || !Objects.equals(supplier.getAccountBookId(), accountBookId)) {
            return null;
        }
        return supplier;
    }

    private PurchaseOrderEntity findTenantPurchaseOrder(Long id, Long companyId, Long accountBookId) {
        PurchaseOrderEntity order = purchaseOrderMapper.selectById(id);
        if (order == null
                || !Objects.equals(order.getCompanyId(), companyId)
                || !Objects.equals(order.getAccountBookId(), accountBookId)) {
            return null;
        }
        return order;
    }

    private DisplayMaps loadDisplayMaps(List<InventoryReplenishmentSuggestionEntity> records, AuditMetadata audit) {
        Map<Long, WarehouseEntity> warehouses = selectWarehouseMap(records, audit);
        Map<Long, ProductEntity> products = selectProductMap(records, audit);
        Map<Long, SupplierEntity> suppliers = selectSupplierMap(records, audit);
        Map<Long, PurchaseOrderEntity> purchaseOrders = selectPurchaseOrderMap(records, audit);
        return new DisplayMaps(warehouses, products, suppliers, purchaseOrders);
    }

    private Map<Long, WarehouseEntity> selectWarehouseMap(
            List<InventoryReplenishmentSuggestionEntity> records,
            AuditMetadata audit
    ) {
        Set<Long> ids = records.stream()
                .map(InventoryReplenishmentSuggestionEntity::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(WarehouseEntity::getId, entity -> entity, (left, right) -> left));
    }

    private Map<Long, ProductEntity> selectProductMap(
            List<InventoryReplenishmentSuggestionEntity> records,
            AuditMetadata audit
    ) {
        Set<Long> ids = records.stream()
                .map(InventoryReplenishmentSuggestionEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(ProductEntity::getId, entity -> entity, (left, right) -> left));
    }

    private Map<Long, SupplierEntity> selectSupplierMap(
            List<InventoryReplenishmentSuggestionEntity> records,
            AuditMetadata audit
    ) {
        Set<Long> ids = records.stream()
                .map(InventoryReplenishmentSuggestionEntity::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(SupplierEntity::getId, entity -> entity, (left, right) -> left));
    }

    private Map<Long, PurchaseOrderEntity> selectPurchaseOrderMap(
            List<InventoryReplenishmentSuggestionEntity> records,
            AuditMetadata audit
    ) {
        Set<Long> ids = records.stream()
                .map(InventoryReplenishmentSuggestionEntity::getPurchaseOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderMapper.selectBatchIds(ids).stream()
                .filter(entity -> Objects.equals(entity.getCompanyId(), audit.companyId()))
                .filter(entity -> Objects.equals(entity.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(PurchaseOrderEntity::getId, entity -> entity, (left, right) -> left));
    }

    private String normalizeStatus(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private record DisplayMaps(
            Map<Long, WarehouseEntity> warehouses,
            Map<Long, ProductEntity> products,
            Map<Long, SupplierEntity> suppliers,
            Map<Long, PurchaseOrderEntity> purchaseOrders
    ) {
    }
}

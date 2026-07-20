package com.tuowei.erp.inventory.replenishment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.model.InventoryReplenishmentSuggestionEntity;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryReplenishmentSuggestionService {

    private static final String SOURCE_LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final DateTimeFormatter NO_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryReplenishmentSuggestionMapper suggestionMapper;
    private final InventoryAlertRuleMapper alertRuleMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventoryAlertService inventoryAlertService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public InventoryReplenishmentSuggestionService(
            InventoryReplenishmentSuggestionMapper suggestionMapper,
            InventoryAlertRuleMapper alertRuleMapper,
            InventoryPostingService inventoryPostingService,
            InventoryAlertService inventoryAlertService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            SupplierMapper supplierMapper,
            PurchaseOrderService purchaseOrderService,
            PurchaseOrderMapper purchaseOrderMapper
    ) {
        this.suggestionMapper = suggestionMapper;
        this.alertRuleMapper = alertRuleMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventoryAlertService = inventoryAlertService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.supplierMapper = supplierMapper;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse create(InventoryReplenishmentSuggestionCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAlertRuleEntity rule = requireMatchingRule(request, audit);
        BigDecimal qtyOnHand = inventoryPostingService.getQtyOnHand(
                request.warehouseId(),
                request.productId(),
                audit.companyId(),
                audit.accountBookId()
        );
        if (qtyOnHand.compareTo(rule.getMinQty()) >= 0) {
            throw new IllegalArgumentException("当前库存已高于安全库存，无需生成补货建议");
        }
        BigDecimal suggestedQty = ScalePrecision.quantity(request.suggestedQty());
        if (suggestedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("建议补货数量必须大于0");
        }
        requireNoDraftSuggestion(audit, request.warehouseId(), request.productId());
        WarehouseEntity warehouse = requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        ProductEntity product = requireProduct(request.productId(), audit.companyId(), audit.accountBookId());
        SupplierEntity supplier = request.supplierId() == null
                ? null
                : requireSupplier(request.supplierId(), audit.companyId(), audit.accountBookId());

        LocalDateTime now = audit.now();
        InventoryReplenishmentSuggestionEntity entity = new InventoryReplenishmentSuggestionEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setSuggestionNo(nextSuggestionNo(audit, now.toLocalDate()));
        entity.setSourceType(SOURCE_LOW_STOCK_ALERT);
        entity.setSourceRuleId(rule.getId());
        entity.setWarehouseId(warehouse.getId());
        entity.setProductId(product.getId());
        entity.setSupplierId(supplier == null ? null : supplier.getId());
        entity.setSuggestedQty(suggestedQty);
        entity.setShortageQtySnapshot(ScalePrecision.quantity(rule.getMinQty().subtract(qtyOnHand)));
        entity.setExpectedArrivalDate(request.expectedArrivalDate());
        entity.setStatus(STATUS_DRAFT);
        entity.setRemark(normalizeNullableText(request.remark()));
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        suggestionMapper.insert(entity);

        inventoryAlertService.handle(
                request.warehouseId(),
                request.productId(),
                "RESOLVED",
                "已生成补货建议 " + entity.getSuggestionNo()
        );
        return toResponse(entity, warehouse, product, supplier, null);
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

    @Transactional
    public InventoryReplenishmentSuggestionResponse update(
            Long id,
            InventoryReplenishmentSuggestionUpdateRequest request
    ) {
        InventoryReplenishmentSuggestionEntity entity = requireSuggestion(id);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前补货建议状态不允许编辑");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        SupplierEntity supplier = request.supplierId() == null
                ? null
                : requireSupplier(request.supplierId(), audit.companyId(), audit.accountBookId());
        BigDecimal suggestedQty = ScalePrecision.quantity(request.suggestedQty());
        if (suggestedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("建议补货数量必须大于0");
        }

        entity.setSupplierId(supplier == null ? null : supplier.getId());
        entity.setSuggestedQty(suggestedQty);
        entity.setExpectedArrivalDate(request.expectedArrivalDate());
        entity.setRemark(normalizeNullableText(request.remark()));
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                suggestionMapper.updateById(entity),
                "补货建议已被其他操作修改，请刷新后重试"
        );
        return toResponse(entity);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse cancel(
            Long id,
            InventoryReplenishmentSuggestionCancelRequest request
    ) {
        InventoryReplenishmentSuggestionEntity entity = requireSuggestion(id);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前补货建议状态不允许取消");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(STATUS_CANCELLED);
        entity.setRemark(appendRemark(entity.getRemark(), "取消原因：" + normalizeNullableText(
                request == null ? null : request.reason()
        )));
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                suggestionMapper.updateById(entity),
                "补货建议已被其他操作修改，请刷新后重试"
        );
        return toResponse(entity);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse convertToPurchaseOrder(Long id) {
        InventoryReplenishmentSuggestionEntity entity = requireSuggestion(id);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前补货建议状态不允许转采购订单");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        if (entity.getSupplierId() == null) {
            throw new IllegalArgumentException("请选择供应商后再转采购订单");
        }
        requireSupplier(entity.getSupplierId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(entity.getWarehouseId(), audit.companyId(), audit.accountBookId());
        requireProduct(entity.getProductId(), audit.companyId(), audit.accountBookId());

        String remark = appendSentence(
                "由补货建议 " + entity.getSuggestionNo() + " 生成。",
                entity.getRemark()
        );
        PurchaseOrderResponse order = purchaseOrderService.create(new PurchaseOrderCreateRequest(
                entity.getSupplierId(),
                audit.now().toLocalDate(),
                entity.getExpectedArrivalDate(),
                remark,
                List.of(new PurchaseOrderLineRequest(
                        entity.getProductId(),
                        entity.getSuggestedQty(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "补货建议 " + entity.getSuggestionNo()
                ))
        ));

        entity.setStatus(STATUS_CONVERTED);
        entity.setPurchaseOrderId(order.id());
        entity.setPurchaseOrderNo(order.orderNo());
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                suggestionMapper.updateById(entity),
                "补货建议已被其他操作修改，请刷新后重试"
        );
        return toResponse(entity);
    }

    private InventoryAlertRuleEntity requireMatchingRule(
            InventoryReplenishmentSuggestionCreateRequest request,
            AuditMetadata audit
    ) {
        InventoryAlertRuleEntity rule = alertRuleMapper.selectById(request.ruleId());
        if (rule == null
                || rule.getDeletedFlag() == null
                || rule.getDeletedFlag() != 0
                || !Integer.valueOf(1).equals(rule.getEnabled())
                || !Objects.equals(rule.getCompanyId(), audit.companyId())
                || !Objects.equals(rule.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(rule.getWarehouseId(), request.warehouseId())
                || !Objects.equals(rule.getProductId(), request.productId())) {
            throw new IllegalArgumentException("低库存规则不存在或已停用");
        }
        return rule;
    }

    private void requireNoDraftSuggestion(AuditMetadata audit, Long warehouseId, Long productId) {
        InventoryReplenishmentSuggestionEntity existing = suggestionMapper.selectOne(
                new LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity>()
                        .eq(InventoryReplenishmentSuggestionEntity::getCompanyId, audit.companyId())
                        .eq(InventoryReplenishmentSuggestionEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryReplenishmentSuggestionEntity::getSourceType, SOURCE_LOW_STOCK_ALERT)
                        .eq(InventoryReplenishmentSuggestionEntity::getWarehouseId, warehouseId)
                        .eq(InventoryReplenishmentSuggestionEntity::getProductId, productId)
                        .eq(InventoryReplenishmentSuggestionEntity::getStatus, STATUS_DRAFT)
                        .eq(InventoryReplenishmentSuggestionEntity::getDeletedFlag, 0)
                        .last("limit 1")
        );
        if (existing != null) {
            throw new IllegalArgumentException("已存在待处理补货建议");
        }
    }

    private InventoryReplenishmentSuggestionEntity requireSuggestion(Long id) {
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

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null
                || warehouse.getDeletedFlag() == null
                || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private ProductEntity requireProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null
                || product.getDeletedFlag() == null
                || product.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(product.getStatus())
                || !Objects.equals(product.getCompanyId(), companyId)
                || !Objects.equals(product.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("商品不存在或已停用");
        }
        return product;
    }

    private SupplierEntity requireSupplier(Long id, Long companyId, Long accountBookId) {
        SupplierEntity supplier = supplierMapper.selectById(id);
        if (supplier == null
                || supplier.getDeletedFlag() == null
                || supplier.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(supplier.getStatus())
                || !Objects.equals(supplier.getCompanyId(), companyId)
                || !Objects.equals(supplier.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("供应商不存在或已停用");
        }
        return supplier;
    }

    private String nextSuggestionNo(AuditMetadata audit, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Long count = suggestionMapper.selectCount(new LambdaQueryWrapper<InventoryReplenishmentSuggestionEntity>()
                .eq(InventoryReplenishmentSuggestionEntity::getCompanyId, audit.companyId())
                .eq(InventoryReplenishmentSuggestionEntity::getAccountBookId, audit.accountBookId())
                .ge(InventoryReplenishmentSuggestionEntity::getCreatedTime, start)
                .lt(InventoryReplenishmentSuggestionEntity::getCreatedTime, end));
        long sequence = count == null ? 1L : count + 1L;
        return "RS" + date.format(NO_DATE_FORMATTER) + String.format("%06d", sequence);
    }

    private InventoryReplenishmentSuggestionResponse toResponse(InventoryReplenishmentSuggestionEntity entity) {
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

    private InventoryReplenishmentSuggestionResponse toResponse(
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

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    private String appendRemark(String remark, String suffix) {
        String normalizedRemark = normalizeNullableText(remark);
        String normalizedSuffix = normalizeNullableText(suffix);
        if (normalizedSuffix == null) {
            return normalizedRemark;
        }
        if (normalizedRemark == null) {
            return normalizedSuffix;
        }
        return normalizedRemark + "；" + normalizedSuffix;
    }

    private String appendSentence(String sentence, String remark) {
        String normalizedRemark = normalizeNullableText(remark);
        if (normalizedRemark == null) {
            return sentence;
        }
        return sentence + normalizedRemark;
    }

    private void touch(InventoryReplenishmentSuggestionEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private record DisplayMaps(
            Map<Long, WarehouseEntity> warehouses,
            Map<Long, ProductEntity> products,
            Map<Long, SupplierEntity> suppliers,
            Map<Long, PurchaseOrderEntity> purchaseOrders
    ) {
    }
}

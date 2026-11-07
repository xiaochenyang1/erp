package com.tuowei.erp.inventory.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.model.InventoryAlertDispositionEntity;
import com.tuowei.erp.inventory.alert.model.InventoryAlertRuleEntity;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Write-side rule maintenance and low-stock disposition commands. */
@Service
public class InventoryAlertCommandService {

    private static final String STATUS_IGNORED = "IGNORED";
    private static final String STATUS_RESOLVED = "RESOLVED";

    private final InventoryAlertRuleMapper alertRuleMapper;
    private final InventoryAlertDispositionMapper dispositionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final InventoryAlertQueryService alertQueryService;

    public InventoryAlertCommandService(
            InventoryAlertRuleMapper alertRuleMapper,
            InventoryAlertDispositionMapper dispositionMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductMapper productMapper,
            InventoryAlertQueryService alertQueryService
    ) {
        this.alertRuleMapper = alertRuleMapper;
        this.dispositionMapper = dispositionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.alertQueryService = alertQueryService;
    }

    @Transactional
    public InventoryAlertRuleResponse createRule(InventoryAlertRuleCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        WarehouseEntity warehouse = requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        ProductEntity product = requireProduct(request.productId(), audit.companyId(), audit.accountBookId());
        ensureRuleUnique(audit, request.warehouseId(), request.productId(), null);
        InventoryAlertRuleEntity rule = new InventoryAlertRuleEntity();
        rule.setCompanyId(audit.companyId());
        rule.setAccountBookId(audit.accountBookId());
        rule.setWarehouseId(request.warehouseId());
        rule.setProductId(request.productId());
        rule.setMinQty(ScalePrecision.quantity(request.minQty()));
        rule.setEnabled(1);
        rule.setDeletedFlag(0);
        rule.setRemark(normalizeRemark(request.remark()));
        rule.setCreatedBy(audit.userId());
        rule.setCreatedTime(now);
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(now);
        rule.setVersion(0);
        alertRuleMapper.insert(rule);
        return alertQueryService.toRuleResponse(rule, warehouse, product);
    }

    @Transactional
    public InventoryAlertRuleResponse updateRule(Long id, InventoryAlertRuleUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAlertRuleEntity rule = requireRule(id, audit);
        rule.setMinQty(ScalePrecision.quantity(request.minQty()));
        rule.setRemark(normalizeRemark(request.remark()));
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                alertRuleMapper.updateById(rule),
                "低库存规则已被其他操作修改，请刷新后重试"
        );
        return alertQueryService.toRuleResponse(
                rule,
                requireWarehouse(rule.getWarehouseId(), audit.companyId(), audit.accountBookId()),
                requireProduct(rule.getProductId(), audit.companyId(), audit.accountBookId())
        );
    }

    @Transactional
    public InventoryAlertRuleResponse enableRule(Long id) {
        return updateRuleEnabled(id, true);
    }

    @Transactional
    public InventoryAlertRuleResponse disableRule(Long id) {
        return updateRuleEnabled(id, false);
    }

    @Transactional
    public void handle(Long warehouseId, Long productId, String status, String remark) {
        AuditMetadata audit = auditMetadataFactory.current();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!STATUS_IGNORED.equals(normalizedStatus) && !STATUS_RESOLVED.equals(normalizedStatus)) {
            throw new IllegalArgumentException("处置状态只能为 IGNORED 或 RESOLVED");
        }
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
                warehouseId, productId, audit.companyId(), audit.accountBookId()
        );
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
                        .last("limit 1")
        );
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
                        .last("limit 1")
        );
        if (existing == null) {
            return;
        }
        existing.setDeletedFlag(1);
        existing.setUpdatedBy(audit.userId());
        existing.setUpdatedTime(audit.now());
        dispositionMapper.updateById(existing);
    }

    private InventoryAlertRuleResponse updateRuleEnabled(Long id, boolean enabled) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAlertRuleEntity rule = requireRule(id, audit);
        if (enabled) {
            ensureRuleUnique(audit, rule.getWarehouseId(), rule.getProductId(), rule.getId());
        }
        rule.setEnabled(enabled ? 1 : 0);
        rule.setUpdatedBy(audit.userId());
        rule.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                alertRuleMapper.updateById(rule),
                "低库存规则已被其他操作修改，请刷新后重试"
        );
        return alertQueryService.toRuleResponse(
                rule,
                requireWarehouse(rule.getWarehouseId(), audit.companyId(), audit.accountBookId()),
                requireProduct(rule.getProductId(), audit.companyId(), audit.accountBookId())
        );
    }

    private InventoryAlertRuleEntity requireRule(Long id, AuditMetadata audit) {
        InventoryAlertRuleEntity rule = alertRuleMapper.selectById(id);
        if (rule == null
                || rule.getDeletedFlag() == null
                || rule.getDeletedFlag() != 0
                || !Objects.equals(rule.getCompanyId(), audit.companyId())
                || !Objects.equals(rule.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("低库存规则不存在");
        }
        return rule;
    }

    private void ensureRuleUnique(AuditMetadata audit, Long warehouseId, Long productId, Long excludedId) {
        LambdaQueryWrapper<InventoryAlertRuleEntity> wrapper = new LambdaQueryWrapper<InventoryAlertRuleEntity>()
                .eq(InventoryAlertRuleEntity::getCompanyId, audit.companyId())
                .eq(InventoryAlertRuleEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryAlertRuleEntity::getWarehouseId, warehouseId)
                .eq(InventoryAlertRuleEntity::getProductId, productId)
                .eq(InventoryAlertRuleEntity::getDeletedFlag, 0)
                .last("limit 1");
        if (excludedId != null) {
            wrapper.ne(InventoryAlertRuleEntity::getId, excludedId);
        }
        if (alertRuleMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("该仓库商品已存在低库存规则");
        }
    }

    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        return remark.trim();
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

package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import com.tuowei.erp.system.dict.service.SystemDictService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Write-side creation, updates and status commands for products. */
@Service
public class ProductCommandService {

    private static final String DICT_PRODUCT_TYPE = "product_type";

    private final ProductMapper productMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SystemDictService systemDictService;
    private final ProductQueryService productQueryService;

    public ProductCommandService(
            ProductMapper productMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemDictService systemDictService,
            ProductQueryService productQueryService
    ) {
        this.productMapper = productMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryLotBalanceMapper = inventoryLotBalanceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemDictService = systemDictService;
        this.productQueryService = productQueryService;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        boolean lotControlled = enabled(request.lotControlled());
        boolean shelfLifeControlled = enabled(request.shelfLifeControlled());
        validateLotFlags(lotControlled, shelfLifeControlled);
        String barcode = normalizeBarcode(request.barcode());
        ensureBarcodeAvailable(barcode, null, audit);

        ProductEntity entity = new ProductEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setProductCode(request.productCode());
        entity.setProductName(request.productName());
        entity.setBarcode(barcode);
        entity.setProductType(systemDictService.requireEnabledItem(
                DICT_PRODUCT_TYPE,
                request.productType(),
                "商品类型不在启用字典项中"
        ));
        entity.setCategoryName(request.categoryName());
        entity.setSpecification(request.specification());
        entity.setUnitName(request.unitName());
        applyAuxUnit(entity, request.auxUnitName(), request.conversionFactor());
        entity.setPurchasePrice(request.purchasePrice());
        entity.setSalePrice(request.salePrice());
        entity.setTaxRate(request.taxRate());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setLotControlled(flag(request.lotControlled()));
        entity.setShelfLifeControlled(flag(request.shelfLifeControlled()));
        entity.setInspectionRequired(flag(request.inspectionRequired()));
        entity.setSerialControlled(flag(request.serialControlled()));
        entity.setRemark(request.remark());
        setAudit(entity, audit, now);

        try {
            productMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("商品编码或条码已存在", ex);
        }
        return productQueryService.toResponse(entity);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        ProductEntity entity = productQueryService.requireProduct(id);
        AuditMetadata audit = auditMetadataFactory.current();
        boolean requestLotControlled = enabled(request.lotControlled());
        boolean requestShelfLifeControlled = enabled(request.shelfLifeControlled());
        validateLotFlags(requestLotControlled, requestShelfLifeControlled);
        String barcode = normalizeBarcode(request.barcode());
        ensureBarcodeAvailable(barcode, entity.getId(), audit);
        if (!enabled(entity.getLotControlled()) && requestLotControlled
                && hasAggregateStock(entity.getId(), audit.companyId())) {
            throw new IllegalArgumentException("商品已有库存，不能直接启用批次管理");
        }
        if (enabled(entity.getLotControlled()) && !requestLotControlled
                && hasLotStock(entity.getId(), audit.companyId())) {
            throw new IllegalArgumentException("商品存在批次库存，不能关闭批次管理");
        }
        entity.setProductName(request.productName());
        entity.setBarcode(barcode);
        entity.setCategoryName(request.categoryName());
        entity.setSpecification(request.specification());
        entity.setUnitName(request.unitName());
        applyAuxUnit(entity, request.auxUnitName(), request.conversionFactor());
        entity.setPurchasePrice(request.purchasePrice());
        entity.setSalePrice(request.salePrice());
        entity.setTaxRate(request.taxRate());
        entity.setLotControlled(flag(request.lotControlled()));
        entity.setShelfLifeControlled(flag(request.shelfLifeControlled()));
        entity.setInspectionRequired(flag(request.inspectionRequired()));
        entity.setSerialControlled(flag(request.serialControlled()));
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        try {
            OptimisticLockGuard.requireUpdated(productMapper.updateById(entity), "商品已被其他操作修改，请刷新后重试");
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("商品编码或条码已存在", ex);
        }
        return productQueryService.toResponse(entity);
    }

    @Transactional
    public ProductResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public ProductResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private ProductResponse updateStatus(Long id, String status) {
        ProductEntity entity = productQueryService.requireProduct(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                productMapper.updateById(entity),
                "商品已被其他操作修改，请刷新后重试"
        );
        return productQueryService.toResponse(entity);
    }

    private void ensureBarcodeAvailable(String barcode, Long excludedProductId, AuditMetadata audit) {
        if (barcode == null) {
            return;
        }
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getCompanyId, audit.companyId())
                .eq(ProductEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductEntity::getBarcode, barcode);
        if (excludedProductId != null) {
            wrapper.ne(ProductEntity::getId, excludedProductId);
        }
        wrapper.last("limit 1");
        if (productMapper.selectOne(wrapper) != null) {
            throw new IllegalArgumentException("商品条码已存在");
        }
    }

    private void applyAuxUnit(ProductEntity entity, String auxUnitName, BigDecimal conversionFactor) {
        String normalizedAuxUnit = normalizeNullableText(auxUnitName);
        if (!StringUtils.hasText(normalizedAuxUnit)) {
            entity.setAuxUnitName(null);
            entity.setConversionFactor(null);
            return;
        }
        if (conversionFactor == null || conversionFactor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("启用辅单位时换算率必须大于0（1 辅单位 = N 库存单位）");
        }
        if (normalizedAuxUnit.equals(normalizeNullableText(entity.getUnitName()))) {
            throw new IllegalArgumentException("辅单位不能与库存单位相同");
        }
        entity.setAuxUnitName(normalizedAuxUnit);
        entity.setConversionFactor(conversionFactor.stripTrailingZeros());
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeBarcode(String barcode) {
        String normalized = normalizeNullableText(barcode);
        if (normalized != null && normalized.length() > 128) {
            throw new IllegalArgumentException("商品条码长度不能超过128个字符");
        }
        return normalized;
    }

    private void validateLotFlags(boolean lotControlled, boolean shelfLifeControlled) {
        if (shelfLifeControlled && !lotControlled) {
            throw new IllegalArgumentException("启用效期管理必须同时启用批次管理");
        }
    }

    private boolean hasAggregateStock(Long productId, Long companyId) {
        return inventoryBalanceMapper.exists(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getProductId, productId)
                .and(query -> query.gt(InventoryBalanceEntity::getQtyOnHand, 0)
                        .or()
                        .gt(InventoryBalanceEntity::getQtyReserved, 0)
                        .or()
                        .gt(InventoryBalanceEntity::getAmountOnHand, 0)));
    }

    private boolean hasLotStock(Long productId, Long companyId) {
        return inventoryLotBalanceMapper.exists(new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
                .eq(InventoryLotBalanceEntity::getProductId, productId)
                .and(query -> query.gt(InventoryLotBalanceEntity::getQtyOnHand, 0)
                        .or()
                        .gt(InventoryLotBalanceEntity::getQtyReserved, 0)
                        .or()
                        .gt(InventoryLotBalanceEntity::getAmountOnHand, 0)));
    }

    private boolean enabled(Integer value) {
        return value != null && value == 1;
    }

    private boolean enabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private int flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private void setAudit(ProductEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

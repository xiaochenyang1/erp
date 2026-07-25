package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import com.tuowei.erp.system.dict.service.SystemDictService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductService {

    private static final String DICT_PRODUCT_TYPE = "product_type";
    private static final List<String> PRODUCT_EXPORT_HEADERS = List.of(
            "productCode",
            "productName",
            "barcode",
            "productType",
            "categoryName",
            "specification",
            "unitName",
            "purchasePrice",
            "salePrice",
            "taxRate",
            "status",
            "lotControlled",
            "shelfLifeControlled",
            "inspectionRequired",
            "serialControlled",
            "remark"
    );

    private final ProductMapper productMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SystemDictService systemDictService;

    public ProductService(
            ProductMapper productMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemDictService systemDictService
    ) {
        this.productMapper = productMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryLotBalanceMapper = inventoryLotBalanceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemDictService = systemDictService;
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
        entity.setProductType(systemDictService.requireEnabledItem(DICT_PRODUCT_TYPE, request.productType(), "商品类型不在启用字典项中"));
        entity.setCategoryName(request.categoryName());
        entity.setSpecification(request.specification());
        entity.setUnitName(request.unitName());
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
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        try {
            productMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("商品编码或条码已存在", ex);
        }
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return toResponse(requireProduct(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode) {
        String normalized = normalizeBarcode(barcode);
        if (normalized == null) {
            throw new IllegalArgumentException("商品条码不能为空");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity entity = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getCompanyId, audit.companyId())
                .eq(ProductEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductEntity::getDeletedFlag, 0)
                .eq(ProductEntity::getStatus, "ACTIVE")
                .eq(ProductEntity::getBarcode, normalized)
                .last("limit 1"));
        if (entity == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductPageQuery query) {
        ProductPageQuery safeQuery = query == null ? new ProductPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        String categoryName = normalizeNullableText(safeQuery.getCategoryName());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<ProductEntity> page = new Page<>(pageNo, pageSize);
        Page<ProductEntity> result = productMapper.selectPage(
                page,
                buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, categoryName)
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public StreamingResponseBody exportProducts(ProductPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ProductPageQuery safeQuery = query == null ? new ProductPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, PRODUCT_EXPORT_HEADERS, rowWriter -> {
            String keyword = normalizeNullableText(safeQuery.getKeyword());
            String status = normalizeStatus(safeQuery.getStatus());
            String categoryName = normalizeNullableText(safeQuery.getCategoryName());
            AuditMetadata audit = auditMetadataFactory.current();
            List<ProductEntity> products = productMapper.selectList(
                    buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, categoryName)
            );
            for (ProductEntity entity : products) {
                rowWriter.write(productExportRow(toResponse(entity)));
            }
        }));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        ProductEntity entity = requireProduct(id);
        AuditMetadata audit = auditMetadataFactory.current();
        boolean requestLotControlled = enabled(request.lotControlled());
        boolean requestShelfLifeControlled = enabled(request.shelfLifeControlled());
        validateLotFlags(requestLotControlled, requestShelfLifeControlled);
        String barcode = normalizeBarcode(request.barcode());
        ensureBarcodeAvailable(barcode, entity.getId(), audit);
        if (!enabled(entity.getLotControlled()) && requestLotControlled && hasAggregateStock(entity.getId(), audit.companyId())) {
            throw new IllegalArgumentException("商品已有库存，不能直接启用批次管理");
        }
        if (enabled(entity.getLotControlled()) && !requestLotControlled && hasLotStock(entity.getId(), audit.companyId())) {
            throw new IllegalArgumentException("商品存在批次库存，不能关闭批次管理");
        }
        entity.setProductName(request.productName());
        entity.setBarcode(barcode);
        entity.setCategoryName(request.categoryName());
        entity.setSpecification(request.specification());
        entity.setUnitName(request.unitName());
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
        return toResponse(entity);
    }

    @Transactional
    public ProductResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public ProductResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private ProductEntity requireProduct(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return entity;
    }

    private ProductResponse updateStatus(Long id, String status) {
        ProductEntity entity = requireProduct(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(productMapper.updateById(entity), "商品已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private LambdaQueryWrapper<ProductEntity> buildListQuery(
            Long companyId,
            Long accountBookId,
            String keyword,
            String status,
            String categoryName
    ) {
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getCompanyId, companyId)
                .eq(ProductEntity::getAccountBookId, accountBookId)
                .eq(ProductEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(ProductEntity::getProductCode, keyword)
                    .or()
                    .like(ProductEntity::getProductName, keyword)
                    .or()
                    .like(ProductEntity::getBarcode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductEntity::getStatus, status);
        }
        if (StringUtils.hasText(categoryName)) {
            wrapper.eq(ProductEntity::getCategoryName, categoryName);
        }
        return wrapper.orderByAsc(ProductEntity::getProductCode);
    }

    private List<?> productExportRow(ProductResponse record) {
        return Arrays.asList(
                record.productCode(),
                record.productName(),
                record.barcode(),
                record.productType(),
                record.categoryName(),
                record.specification(),
                record.unitName(),
                record.purchasePrice(),
                record.salePrice(),
                record.taxRate(),
                record.status(),
                record.lotControlled(),
                record.shelfLifeControlled(),
                record.inspectionRequired(),
                record.serialControlled(),
                record.remark()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeBarcode(String barcode) {
        String normalized = normalizeNullableText(barcode);
        if (normalized != null && normalized.length() > 128) {
            throw new IllegalArgumentException("商品条码长度不能超过128个字符");
        }
        return normalized;
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

    private ProductResponse toResponse(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getProductCode(),
                entity.getProductName(),
                entity.getProductType(),
                entity.getCategoryName(),
                entity.getSpecification(),
                entity.getUnitName(),
                entity.getPurchasePrice(),
                entity.getSalePrice(),
                entity.getTaxRate(),
                entity.getStatus(),
                enabled(entity.getLotControlled()),
                enabled(entity.getShelfLifeControlled()),
                enabled(entity.getInspectionRequired()),
                enabled(entity.getSerialControlled()),
                entity.getRemark(),
                entity.getBarcode()
        );
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }

}

package com.tuowei.erp.masterdata.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant guards, response mapping and product export. */
@Service
public class ProductQueryService {

    private static final List<String> PRODUCT_EXPORT_HEADERS = List.of(
            "productCode",
            "productName",
            "barcode",
            "productType",
            "categoryName",
            "specification",
            "unitName",
            "auxUnitName",
            "conversionFactor",
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
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductQueryService(ProductMapper productMapper, AuditMetadataFactory auditMetadataFactory) {
        this.productMapper = productMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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

    /**
     * CSV output is deliberately left outside a transaction. The callback runs
     * after the controller returns and restores the caller's security context
     * while it performs the tenant-scoped read.
     */
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

    ProductEntity requireProduct(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return entity;
    }

    ProductResponse toResponse(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getProductCode(),
                entity.getProductName(),
                entity.getProductType(),
                entity.getCategoryName(),
                entity.getSpecification(),
                entity.getUnitName(),
                entity.getAuxUnitName(),
                entity.getConversionFactor(),
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
                record.auxUnitName(),
                record.conversionFactor(),
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

    private boolean enabled(Integer value) {
        return value != null && value == 1;
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

package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ProductImportHandler extends AbstractImportHandler {

    private final ProductMapper productMapper;

    public ProductImportHandler(ImportValidationSupport support, ProductMapper productMapper) {
        super(support);
        this.productMapper = productMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.PRODUCT;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String productCode = support.required(raw, "product_code", errors);
        String productName = support.required(raw, "product_name", errors);
        String unitName = support.required(raw, "unit_name", errors);
        String auxUnitName = support.optionalText(raw, "aux_unit_name");
        if (productCode != null) {
            support.duplicateInFile(seen(context, "productCode"), productCode, "product_code", errors);
            Long count = productMapper.selectCount(new LambdaQueryWrapper<ProductEntity>()
                    .eq(ProductEntity::getCompanyId, context.companyId())
                    .eq(ProductEntity::getAccountBookId, context.accountBookId())
                    .eq(ProductEntity::getProductCode, productCode)
                    .eq(ProductEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("product_code", "商品编码已存在"));
            }
        }
        BigDecimal purchasePrice = support.optionalAmount(raw, "purchase_price", BigDecimal.ZERO, errors);
        BigDecimal salePrice = support.optionalAmount(raw, "sale_price", BigDecimal.ZERO, errors);
        BigDecimal taxRate = support.optionalAmount(raw, "tax_rate", BigDecimal.ZERO, errors);
        BigDecimal conversionFactor = null;
        if (auxUnitName != null) {
            if (unitName != null && auxUnitName.equalsIgnoreCase(unitName.trim())) {
                errors.add(new ImportRowErrorResponse("aux_unit_name", "辅单位不能与库存单位相同"));
            }
            if (!org.springframework.util.StringUtils.hasText(raw.get("conversion_factor"))) {
                errors.add(new ImportRowErrorResponse("conversion_factor", "启用辅单位时换算率必须大于0（1 辅单位 = N 库存单位）"));
            } else {
                conversionFactor = support.quantity(raw, "conversion_factor", errors);
                if (conversionFactor.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new ImportRowErrorResponse("conversion_factor", "启用辅单位时换算率必须大于0（1 辅单位 = N 库存单位）"));
                    conversionFactor = null;
                }
            }
        } else if (org.springframework.util.StringUtils.hasText(raw.get("conversion_factor"))) {
            errors.add(new ImportRowErrorResponse("conversion_factor", "未填写辅单位时不能填写换算率"));
        }
        rejectNegative("purchase_price", purchasePrice, errors);
        rejectNegative("sale_price", salePrice, errors);
        rejectNegative("tax_rate", taxRate, errors);
        normalized.put("productCode", productCode);
        normalized.put("productName", productName);
        normalized.put("productType", support.optionalText(raw, "product_type", "STANDARD"));
        normalized.put("categoryName", support.optionalText(raw, "category_name"));
        normalized.put("specification", support.optionalText(raw, "specification"));
        normalized.put("unitName", unitName);
        normalized.put("auxUnitName", auxUnitName);
        normalized.put("conversionFactor", conversionFactor);
        normalized.put("purchasePrice", purchasePrice);
        normalized.put("salePrice", salePrice);
        normalized.put("taxRate", taxRate);
        normalized.put("status", support.optionalText(raw, "status", "ACTIVE"));
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        LocalDateTime now = audit.now();
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            ProductEntity entity = new ProductEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setProductCode(text(normalized, "productCode"));
            entity.setProductName(text(normalized, "productName"));
            entity.setProductType(text(normalized, "productType"));
            entity.setCategoryName(text(normalized, "categoryName"));
            entity.setSpecification(text(normalized, "specification"));
            entity.setUnitName(text(normalized, "unitName"));
            entity.setAuxUnitName(text(normalized, "auxUnitName"));
            Object conversionFactor = normalized.get("conversionFactor");
            if (conversionFactor instanceof BigDecimal factor) {
                entity.setConversionFactor(factor.stripTrailingZeros());
            } else if (conversionFactor != null) {
                entity.setConversionFactor(new BigDecimal(conversionFactor.toString()).stripTrailingZeros());
            }
            entity.setPurchasePrice(decimalValue(normalized, "purchasePrice"));
            entity.setSalePrice(decimalValue(normalized, "salePrice"));
            entity.setTaxRate(decimalValue(normalized, "taxRate"));
            entity.setStatus(text(normalized, "status"));
            entity.setDeletedFlag(0);
            entity.setRemark(text(normalized, "remark"));
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            productMapper.insert(entity);
        }
        return rows.size();
    }
}

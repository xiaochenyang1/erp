package com.tuowei.erp.masterdata.product.web;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String productCode,
        String productName,
        String productType,
        String categoryName,
        String specification,
        String unitName,
        String auxUnitName,
        BigDecimal conversionFactor,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal taxRate,
        String status,
        Boolean lotControlled,
        Boolean shelfLifeControlled,
        Boolean inspectionRequired,
        Boolean serialControlled,
        String remark,
        String barcode
) {
    public ProductResponse(
            Long id,
            String productCode,
            String productName,
            String productType,
            String categoryName,
            String specification,
            String unitName,
            BigDecimal purchasePrice,
            BigDecimal salePrice,
            BigDecimal taxRate,
            String status,
            Boolean lotControlled,
            Boolean shelfLifeControlled,
            Boolean inspectionRequired,
            Boolean serialControlled,
            String remark,
            String barcode
    ) {
        this(
                id,
                productCode,
                productName,
                productType,
                categoryName,
                specification,
                unitName,
                null,
                null,
                purchasePrice,
                salePrice,
                taxRate,
                status,
                lotControlled,
                shelfLifeControlled,
                inspectionRequired,
                serialControlled,
                remark,
                barcode
        );
    }
}

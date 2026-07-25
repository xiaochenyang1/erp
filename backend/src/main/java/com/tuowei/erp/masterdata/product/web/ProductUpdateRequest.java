package com.tuowei.erp.masterdata.product.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank(message = "productName不能为空") String productName,
        @NotBlank(message = "categoryName不能为空") String categoryName,
        String specification,
        @NotBlank(message = "unitName不能为空") String unitName,
        String auxUnitName,
        BigDecimal conversionFactor,
        @NotNull(message = "purchasePrice不能为空") BigDecimal purchasePrice,
        @NotNull(message = "salePrice不能为空") BigDecimal salePrice,
        @NotNull(message = "taxRate不能为空") BigDecimal taxRate,
        Boolean lotControlled,
        Boolean shelfLifeControlled,
        Boolean inspectionRequired,
        Boolean serialControlled,
        String remark,
        @Size(max = 128, message = "商品条码长度不能超过128个字符") String barcode
) {
    public ProductUpdateRequest(
            String productName,
            String categoryName,
            String specification,
            String unitName,
            BigDecimal purchasePrice,
            BigDecimal salePrice,
            BigDecimal taxRate,
            Boolean lotControlled,
            Boolean shelfLifeControlled,
            Boolean inspectionRequired,
            Boolean serialControlled,
            String remark,
            String barcode
    ) {
        this(
                productName,
                categoryName,
                specification,
                unitName,
                null,
                null,
                purchasePrice,
                salePrice,
                taxRate,
                lotControlled,
                shelfLifeControlled,
                inspectionRequired,
                serialControlled,
                remark,
                barcode
        );
    }
}

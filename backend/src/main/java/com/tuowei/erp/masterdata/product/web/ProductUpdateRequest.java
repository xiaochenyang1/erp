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
        @NotNull(message = "purchasePrice不能为空") BigDecimal purchasePrice,
        @NotNull(message = "salePrice不能为空") BigDecimal salePrice,
        @NotNull(message = "taxRate不能为空") BigDecimal taxRate,
        Boolean lotControlled,
        Boolean shelfLifeControlled,
        Boolean inspectionRequired,
        String remark,
        @Size(max = 128, message = "商品条码长度不能超过128个字符") String barcode
) {
}

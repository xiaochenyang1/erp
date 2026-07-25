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
}

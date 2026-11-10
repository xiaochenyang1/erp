package com.tuowei.erp.masterdata.supplierproduct.web;
import java.math.BigDecimal;
public record SupplierProductRelationResponse(Long id, Long supplierId, Long productId, String productCode, String productName, String supplierProductCode, String supplierProductName, BigDecimal minPurchaseQty, Integer leadTimeDays, Boolean defaultSupplier, String remark, String status) {}

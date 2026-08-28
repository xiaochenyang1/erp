package com.tuowei.erp.masterdata.supplierproduct.web;
import jakarta.validation.constraints.DecimalMin; import jakarta.validation.constraints.NotNull; import java.math.BigDecimal;
public record SupplierProductRelationRequest(@NotNull Long productId, String supplierProductCode, String supplierProductName, @DecimalMin("0") BigDecimal minPurchaseQty, @DecimalMin("0") Integer leadTimeDays, Boolean defaultSupplier, String remark) {}

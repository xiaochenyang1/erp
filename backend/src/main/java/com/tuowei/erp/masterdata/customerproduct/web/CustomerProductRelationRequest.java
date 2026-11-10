package com.tuowei.erp.masterdata.customerproduct.web;
import jakarta.validation.constraints.NotNull;
public record CustomerProductRelationRequest(@NotNull Long productId, String customerProductCode, String customerProductName, String deliveryPreference, String packagingPreference, String remark) {}

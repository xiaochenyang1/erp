package com.tuowei.erp.masterdata.customerproduct.web;
public record CustomerProductRelationResponse(Long id, Long customerId, Long productId, String productCode, String productName, String customerProductCode, String customerProductName, String deliveryPreference, String packagingPreference, String remark, String status) {}

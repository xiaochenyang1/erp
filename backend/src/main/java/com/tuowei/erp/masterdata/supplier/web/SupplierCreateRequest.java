package com.tuowei.erp.masterdata.supplier.web;

import jakarta.validation.constraints.NotBlank;

public record SupplierCreateRequest(
        @NotBlank(message = "supplierCode不能为空") String supplierCode,
        @NotBlank(message = "supplierName不能为空") String supplierName,
        String contactName,
        String contactPhone,
        @NotBlank(message = "settlementMethod不能为空") String settlementMethod,
        String address,
        String remark
) {
}

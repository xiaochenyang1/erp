package com.tuowei.erp.masterdata.supplier.web;

import jakarta.validation.constraints.NotBlank;

public record SupplierUpdateRequest(
        @NotBlank(message = "supplierName不能为空") String supplierName,
        String contactName,
        String contactPhone,
        String email,
        @NotBlank(message = "settlementMethod不能为空") String settlementMethod,
        Integer creditPeriod,
        String address,
        String status,
        String remark
) {
}

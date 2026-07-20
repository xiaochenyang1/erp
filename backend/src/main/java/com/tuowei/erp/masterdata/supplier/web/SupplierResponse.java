package com.tuowei.erp.masterdata.supplier.web;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        String contactName,
        String contactPhone,
        String settlementMethod,
        String address,
        String status,
        String remark
) {
}

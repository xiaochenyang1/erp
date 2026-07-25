package com.tuowei.erp.masterdata.location.web;

public record LocationResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        String locationCode,
        String locationName,
        boolean isDefault,
        String status,
        String remark
) {
}

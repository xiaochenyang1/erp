package com.tuowei.erp.masterdata.warehouse.web;

public record WarehouseResponse(
        Long id,
        String warehouseCode,
        String warehouseName,
        Long deptId,
        Long managerUserId,
        String address,
        String status,
        String remark
) {
}

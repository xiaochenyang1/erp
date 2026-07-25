package com.tuowei.erp.inventory.mrp.web;

public record MrpConvertLineRequest(
        Long supplierId,
        Long finishedWarehouseId,
        Long materialWarehouseId
) {
}

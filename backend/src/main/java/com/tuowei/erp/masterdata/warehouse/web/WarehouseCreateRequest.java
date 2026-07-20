package com.tuowei.erp.masterdata.warehouse.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WarehouseCreateRequest(
        @NotBlank(message = "warehouseCode不能为空") String warehouseCode,
        @NotBlank(message = "warehouseName不能为空") String warehouseName,
        @NotNull(message = "deptId不能为空") Long deptId,
        @NotNull(message = "managerUserId不能为空") Long managerUserId,
        String address,
        String remark
) {
}

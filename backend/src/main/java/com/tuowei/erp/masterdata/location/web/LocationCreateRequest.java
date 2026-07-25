package com.tuowei.erp.masterdata.location.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocationCreateRequest(
        @NotNull(message = "warehouseId不能为空") Long warehouseId,
        @NotBlank(message = "locationCode不能为空") String locationCode,
        @NotBlank(message = "locationName不能为空") String locationName,
        Boolean isDefault,
        String remark
) {
}

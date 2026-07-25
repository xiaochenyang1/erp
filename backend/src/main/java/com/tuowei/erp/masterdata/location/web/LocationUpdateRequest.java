package com.tuowei.erp.masterdata.location.web;

import jakarta.validation.constraints.NotBlank;

public record LocationUpdateRequest(
        @NotBlank(message = "locationCode不能为空") String locationCode,
        @NotBlank(message = "locationName不能为空") String locationName,
        Boolean isDefault,
        String status,
        String remark
) {
}

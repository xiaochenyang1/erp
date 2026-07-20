package com.tuowei.erp.system.config.web;

import jakarta.validation.constraints.NotBlank;

public record SystemConfigUpdateRequest(
        @NotBlank(message = "configName不能为空") String configName,
        @NotBlank(message = "configValue不能为空") String configValue,
        String remark
) {
}

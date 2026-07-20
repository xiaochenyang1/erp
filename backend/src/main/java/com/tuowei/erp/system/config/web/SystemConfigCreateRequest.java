package com.tuowei.erp.system.config.web;

import jakarta.validation.constraints.NotBlank;

public record SystemConfigCreateRequest(
        @NotBlank(message = "configCode不能为空") String configCode,
        @NotBlank(message = "configName不能为空") String configName,
        @NotBlank(message = "configValue不能为空") String configValue,
        String remark
) {
}

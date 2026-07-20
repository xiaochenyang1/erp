package com.tuowei.erp.system.config.web;

public record SystemConfigResponse(
        Long id,
        String configCode,
        String configName,
        String configValue,
        String status,
        String remark
) {
}

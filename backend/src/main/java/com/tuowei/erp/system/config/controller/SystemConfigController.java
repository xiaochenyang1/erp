package com.tuowei.erp.system.config.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.config.service.SystemConfigService;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/configs")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_CREATE)
    @PostMapping
    public ApiResponse<SystemConfigResponse> create(@Valid @RequestBody SystemConfigCreateRequest request) {
        return ApiResponse.success(systemConfigService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SystemConfigResponse>> list(SystemConfigPageQuery query) {
        return ApiResponse.success(systemConfigService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SystemConfigResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(systemConfigService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<SystemConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SystemConfigUpdateRequest request
    ) {
        return ApiResponse.success(systemConfigService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<SystemConfigResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(systemConfigService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<SystemConfigResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(systemConfigService.disable(id));
    }
}

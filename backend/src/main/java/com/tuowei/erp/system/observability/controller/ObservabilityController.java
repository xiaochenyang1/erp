package com.tuowei.erp.system.observability.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/observability")
public class ObservabilityController {

    private final ObservabilityBusinessHealthService businessHealthService;

    public ObservabilityController(ObservabilityBusinessHealthService businessHealthService) {
        this.businessHealthService = businessHealthService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_OBSERVABILITY_VIEW)
    @GetMapping("/business-health")
    public ApiResponse<BusinessHealthResponse> businessHealth() {
        return ApiResponse.success(businessHealthService.current());
    }
}

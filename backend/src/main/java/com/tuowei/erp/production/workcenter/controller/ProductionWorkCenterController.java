package com.tuowei.erp.production.workcenter.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
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
@RequestMapping("/api/production/work-centers")
public class ProductionWorkCenterController {

    private final ProductionWorkCenterService productionWorkCenterService;

    public ProductionWorkCenterController(ProductionWorkCenterService productionWorkCenterService) {
        this.productionWorkCenterService = productionWorkCenterService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_CREATE)
    @PostMapping
    public ApiResponse<ProductionWorkCenterResponse> create(
            @Valid @RequestBody ProductionWorkCenterCreateRequest request
    ) {
        return ApiResponse.success(productionWorkCenterService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionWorkCenterResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductionWorkCenterUpdateRequest request
    ) {
        return ApiResponse.success(productionWorkCenterService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionWorkCenterResponse>> list(ProductionWorkCenterPageQuery query) {
        return ApiResponse.success(productionWorkCenterService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionWorkCenterResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ProductionWorkCenterResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_WORK_CENTER_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ProductionWorkCenterResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(productionWorkCenterService.disable(id));
    }
}

package com.tuowei.erp.production.routing.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
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
@RequestMapping("/api/production/routings")
public class ProductionRoutingController {

    private final ProductionRoutingService productionRoutingService;

    public ProductionRoutingController(ProductionRoutingService productionRoutingService) {
        this.productionRoutingService = productionRoutingService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_CREATE)
    @PostMapping
    public ApiResponse<ProductionRoutingResponse> create(
            @Valid @RequestBody ProductionRoutingCreateRequest request
    ) {
        return ApiResponse.success(productionRoutingService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionRoutingResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductionRoutingUpdateRequest request
    ) {
        return ApiResponse.success(productionRoutingService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionRoutingResponse>> list(ProductionRoutingPageQuery query) {
        return ApiResponse.success(productionRoutingService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionRoutingResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ProductionRoutingResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_ROUTING_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ProductionRoutingResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(productionRoutingService.disable(id));
    }
}

package com.tuowei.erp.production.bom.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
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
@RequestMapping("/api/production/boms")
public class ProductionBomController {

    private final ProductionBomService productionBomService;

    public ProductionBomController(ProductionBomService productionBomService) {
        this.productionBomService = productionBomService;
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_BOM_MANAGE)
    @PostMapping
    public ApiResponse<ProductionBomResponse> create(@Valid @RequestBody ProductionBomCreateRequest request) {
        return ApiResponse.success(productionBomService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_BOM_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<ProductionBomResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductionBomUpdateRequest request
    ) {
        return ApiResponse.success(productionBomService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_BOM_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductionBomResponse>> list(ProductionBomPageQuery query) {
        return ApiResponse.success(productionBomService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PRODUCTION_BOM_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductionBomResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productionBomService.getById(id));
    }
}

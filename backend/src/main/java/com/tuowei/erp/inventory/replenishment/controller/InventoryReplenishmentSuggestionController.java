package com.tuowei.erp.inventory.replenishment.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionService;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryReplenishmentSuggestionController {

    private final InventoryReplenishmentSuggestionService replenishmentSuggestionService;

    public InventoryReplenishmentSuggestionController(
            InventoryReplenishmentSuggestionService replenishmentSuggestionService
    ) {
        this.replenishmentSuggestionService = replenishmentSuggestionService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_VIEW)
    @GetMapping("/api/inventory/replenishment-suggestions")
    public ApiResponse<PageResponse<InventoryReplenishmentSuggestionResponse>> list(
            InventoryReplenishmentSuggestionPageQuery query
    ) {
        return ApiResponse.success(replenishmentSuggestionService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CREATE)
    @PostMapping("/api/inventory/replenishment-suggestions")
    public ApiResponse<InventoryReplenishmentSuggestionResponse> create(
            @Valid @RequestBody InventoryReplenishmentSuggestionCreateRequest request
    ) {
        return ApiResponse.success(replenishmentSuggestionService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_UPDATE)
    @PutMapping("/api/inventory/replenishment-suggestions/{id}")
    public ApiResponse<InventoryReplenishmentSuggestionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryReplenishmentSuggestionUpdateRequest request
    ) {
        return ApiResponse.success(replenishmentSuggestionService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CANCEL)
    @PostMapping("/api/inventory/replenishment-suggestions/{id}/cancel")
    public ApiResponse<InventoryReplenishmentSuggestionResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) InventoryReplenishmentSuggestionCancelRequest request
    ) {
        return ApiResponse.success(replenishmentSuggestionService.cancel(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_REPLENISHMENT_CONVERT)
    @PostMapping("/api/inventory/replenishment-suggestions/{id}/convert-to-purchase-order")
    public ApiResponse<InventoryReplenishmentSuggestionResponse> convertToPurchaseOrder(@PathVariable Long id) {
        return ApiResponse.success(replenishmentSuggestionService.convertToPurchaseOrder(id));
    }
}

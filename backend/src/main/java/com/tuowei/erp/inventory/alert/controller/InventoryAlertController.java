package com.tuowei.erp.inventory.alert.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryAlertHandleRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InventoryAlertController {

    private final InventoryAlertService inventoryAlertService;

    public InventoryAlertController(InventoryAlertService inventoryAlertService) {
        this.inventoryAlertService = inventoryAlertService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ALERT_CREATE)
    @PostMapping("/api/inventory/alert-rules")
    public ApiResponse<InventoryAlertRuleResponse> createRule(@Valid @RequestBody InventoryAlertRuleCreateRequest request) {
        return ApiResponse.success(inventoryAlertService.createRule(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ALERT_VIEW)
    @GetMapping("/api/inventory/low-stock")
    public ApiResponse<List<InventoryLowStockResponse>> listLowStock(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId
    ) {
        return ApiResponse.success(inventoryAlertService.listLowStock(warehouseId, productId));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ALERT_HANDLE)
    @PostMapping("/api/inventory/low-stock/ignore")
    public ApiResponse<Void> ignoreLowStock(@Valid @RequestBody InventoryAlertHandleRequest request) {
        inventoryAlertService.handle(request.warehouseId(), request.productId(), "IGNORED", request.remark());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ALERT_HANDLE)
    @PostMapping("/api/inventory/low-stock/resolve")
    public ApiResponse<Void> resolveLowStock(@Valid @RequestBody InventoryAlertHandleRequest request) {
        inventoryAlertService.handle(request.warehouseId(), request.productId(), "RESOLVED", request.remark());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ALERT_HANDLE)
    @PostMapping("/api/inventory/low-stock/reactivate")
    public ApiResponse<Void> reactivateLowStock(@Valid @RequestBody InventoryAlertHandleRequest request) {
        inventoryAlertService.reactivate(request.warehouseId(), request.productId());
        return ApiResponse.success(null);
    }
}

package com.tuowei.erp.inventory.adjust.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentPageQuery;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/adjustments")
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    public InventoryAdjustmentController(InventoryAdjustmentService inventoryAdjustmentService) {
        this.inventoryAdjustmentService = inventoryAdjustmentService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_CREATE)
    @PostMapping
    @OperationLog(module = "inventory", operation = "create-adjustment", message = "创建库存调整单", bizNo = "#result.data.adjustmentNo")
    public ApiResponse<InventoryAdjustmentResponse> create(@Valid @RequestBody InventoryAdjustmentCreateRequest request) {
        return ApiResponse.success(inventoryAdjustmentService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InventoryAdjustmentResponse>> list(InventoryAdjustmentPageQuery query) {
        return ApiResponse.success(inventoryAdjustmentService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<InventoryAdjustmentResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(inventoryAdjustmentService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_POST)
    @PostMapping("/{id}/post")
    @OperationLog(module = "inventory", operation = "post-adjustment", message = "过账库存调整单", bizNo = "#result.data.adjustmentNo")
    public ApiResponse<InventoryAdjustmentResponse> post(@PathVariable Long id) {
        return ApiResponse.success(inventoryAdjustmentService.post(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_ADJUSTMENT_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "inventory", operation = "cancel-adjustment", message = "取消库存调整单", bizNo = "#result.data.adjustmentNo")
    public ApiResponse<InventoryAdjustmentResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(inventoryAdjustmentService.cancel(id));
    }
}

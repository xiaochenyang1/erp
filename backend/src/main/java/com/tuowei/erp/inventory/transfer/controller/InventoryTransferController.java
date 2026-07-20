package com.tuowei.erp.inventory.transfer.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferService;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferPageQuery;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferResponse;
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
@RequestMapping("/api/inventory/transfers")
public class InventoryTransferController {

    private final InventoryTransferService inventoryTransferService;

    public InventoryTransferController(InventoryTransferService inventoryTransferService) {
        this.inventoryTransferService = inventoryTransferService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_TRANSFER_CREATE)
    @PostMapping
    @OperationLog(module = "inventory", operation = "create-transfer", message = "创建库存调拨单", bizNo = "#result.data.transferNo")
    public ApiResponse<InventoryTransferResponse> create(@Valid @RequestBody InventoryTransferCreateRequest request) {
        return ApiResponse.success(inventoryTransferService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_TRANSFER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InventoryTransferResponse>> list(InventoryTransferPageQuery query) {
        return ApiResponse.success(inventoryTransferService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_TRANSFER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<InventoryTransferResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(inventoryTransferService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_TRANSFER_POST)
    @PostMapping("/{id}/post")
    @OperationLog(module = "inventory", operation = "post-transfer", message = "过账库存调拨单", bizNo = "#result.data.transferNo")
    public ApiResponse<InventoryTransferResponse> post(@PathVariable Long id) {
        return ApiResponse.success(inventoryTransferService.post(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_TRANSFER_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "inventory", operation = "cancel-transfer", message = "取消库存调拨单", bizNo = "#result.data.transferNo")
    public ApiResponse<InventoryTransferResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(inventoryTransferService.cancel(id));
    }
}

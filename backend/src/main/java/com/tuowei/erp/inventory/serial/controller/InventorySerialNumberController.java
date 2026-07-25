package com.tuowei.erp.inventory.serial.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.serial.web.InventorySerialCreateRequest;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.inventory.serial.web.InventorySerialResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/serials")
public class InventorySerialNumberController {

    private final InventorySerialNumberService serialNumberService;

    public InventorySerialNumberController(InventorySerialNumberService serialNumberService) {
        this.serialNumberService = serialNumberService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_SERIAL_MANAGE)
    @OperationLog(module = "inventory", operation = "serial-create", bizNo = "#result.data.serialNo")
    @PostMapping
    public ApiResponse<InventorySerialResponse> create(@Valid @RequestBody InventorySerialCreateRequest request) {
        return ApiResponse.success(serialNumberService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_SERIAL_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InventorySerialResponse>> list(InventorySerialPageQuery query) {
        return ApiResponse.success(serialNumberService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_SERIAL_MANAGE)
    @OperationLog(module = "inventory", operation = "serial-issue", bizNo = "#id")
    @PostMapping("/{id}/issue")
    public ApiResponse<InventorySerialResponse> issue(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String bizType = body == null ? null : body.get("outboundBizType");
        String bizNo = body == null ? null : body.get("outboundBizNo");
        return ApiResponse.success(serialNumberService.issue(id, bizType, bizNo));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_SERIAL_MANAGE)
    @OperationLog(module = "inventory", operation = "serial-scrap", bizNo = "#id")
    @PostMapping("/{id}/scrap")
    public ApiResponse<InventorySerialResponse> scrap(@PathVariable Long id) {
        return ApiResponse.success(serialNumberService.scrap(id));
    }
}

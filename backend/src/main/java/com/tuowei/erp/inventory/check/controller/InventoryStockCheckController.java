package com.tuowei.erp.inventory.check.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckCreateRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckPageQuery;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
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
@RequestMapping("/api/inventory/checks")
public class InventoryStockCheckController {

    private final InventoryStockCheckService stockCheckService;

    public InventoryStockCheckController(InventoryStockCheckService stockCheckService) {
        this.stockCheckService = stockCheckService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_CREATE)
    @PostMapping
    @OperationLog(module = "inventory", operation = "create-check", message = "创建库存盘点任务", bizNo = "#result.data.checkNo")
    public ApiResponse<InventoryStockCheckResponse> create(@Valid @RequestBody InventoryStockCheckCreateRequest request) {
        return ApiResponse.success(stockCheckService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InventoryStockCheckResponse>> list(InventoryStockCheckPageQuery query) {
        return ApiResponse.success(stockCheckService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<InventoryStockCheckResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(stockCheckService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_CREATE)
    @PutMapping("/{id}")
    @OperationLog(module = "inventory", operation = "update-check", message = "编辑库存盘点结果", bizNo = "#result.data.checkNo")
    public ApiResponse<InventoryStockCheckResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryStockCheckUpdateRequest request
    ) {
        return ApiResponse.success(stockCheckService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_ADJUST)
    @PostMapping("/{id}/adjust")
    @OperationLog(module = "inventory", operation = "adjust-check", message = "生成盘点差异调整", bizNo = "#result.data.checkNo")
    public ApiResponse<InventoryStockCheckResponse> adjust(@PathVariable Long id) {
        return ApiResponse.success(stockCheckService.postAdjustment(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_CHECK_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "inventory", operation = "cancel-check", message = "取消库存盘点", bizNo = "#result.data.checkNo")
    public ApiResponse<InventoryStockCheckResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(stockCheckService.cancel(id));
    }
}

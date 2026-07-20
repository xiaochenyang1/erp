package com.tuowei.erp.sales.delivery.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
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
@RequestMapping("/api/sales/deliveries")
public class SalesDeliveryController {

    private final SalesDeliveryService salesDeliveryService;

    public SalesDeliveryController(SalesDeliveryService salesDeliveryService) {
        this.salesDeliveryService = salesDeliveryService;
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_CREATE)
    @PostMapping
    @OperationLog(module = "sales", operation = "create-delivery", message = "创建销售出库单", bizNo = "#result.data.deliveryNo")
    public ApiResponse<SalesDeliveryResponse> create(@Valid @RequestBody SalesDeliveryCreateRequest request) {
        return ApiResponse.success(salesDeliveryService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SalesDeliveryResponse>> list(SalesDeliveryPageQuery query) {
        return ApiResponse.success(salesDeliveryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SalesDeliveryResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesDeliveryService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_UPDATE)
    @PutMapping("/{id}")
    @OperationLog(module = "sales", operation = "update-delivery", message = "更新销售出库单", bizNo = "#result.data.deliveryNo")
    public ApiResponse<SalesDeliveryResponse> update(@PathVariable Long id, @Valid @RequestBody SalesDeliveryUpdateRequest request) {
        return ApiResponse.success(salesDeliveryService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "sales", operation = "cancel-delivery", message = "取消销售出库单", bizNo = "#result.data.deliveryNo")
    public ApiResponse<SalesDeliveryResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(salesDeliveryService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_DELIVERY_POST)
    @PostMapping("/{id}/post")
    @OperationLog(module = "sales", operation = "post-delivery", message = "过账销售出库单", bizNo = "#result.data.deliveryNo")
    public ApiResponse<SalesDeliveryResponse> post(@PathVariable Long id) {
        return ApiResponse.success(salesDeliveryService.post(id));
    }
}

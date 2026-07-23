package com.tuowei.erp.sales.order.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest;
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
@RequestMapping("/api/sales/orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_CREATE)
    @PostMapping
    @OperationLog(module = "sales", operation = "create", message = "创建销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderCreateRequest request) {
        return ApiResponse.success(salesOrderService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SalesOrderResponse>> list(SalesOrderPageQuery query) {
        return ApiResponse.success(salesOrderService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SalesOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesOrderService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_VIEW)
    @PostMapping("/credit-preview")
    public ApiResponse<SalesOrderCreditPreviewResponse> creditPreview(@Valid @RequestBody SalesOrderCreditPreviewRequest request) {
        return ApiResponse.success(salesOrderService.previewCredit(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_UPDATE)
    @PutMapping("/{id}")
    @OperationLog(module = "sales", operation = "update", message = "更新销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> update(@PathVariable Long id, @Valid @RequestBody SalesOrderUpdateRequest request) {
        return ApiResponse.success(salesOrderService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_SUBMIT)
    @PostMapping("/{id}/submit")
    @OperationLog(module = "sales", operation = "submit", message = "提交销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> submit(@PathVariable Long id, @Valid @RequestBody(required = false) SalesOrderSubmitRequest request) {
        SalesOrderSubmitRequest safeRequest = request == null ? new SalesOrderSubmitRequest(null) : request;
        return ApiResponse.success(salesOrderService.submit(id, safeRequest));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_APPROVE)
    @PostMapping("/{id}/approve")
    @OperationLog(module = "sales", operation = "approve", message = "审核通过销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> approve(@PathVariable Long id, @Valid @RequestBody(required = false) SalesOrderApproveRequest request) {
        SalesOrderApproveRequest safeRequest = request == null ? new SalesOrderApproveRequest(null) : request;
        return ApiResponse.success(salesOrderService.approve(id, safeRequest));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_UNAPPROVE)
    @PostMapping("/{id}/unapprove")
    @OperationLog(module = "sales", operation = "unapprove", message = "反审核销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> unapprove(@PathVariable Long id) {
        return ApiResponse.success(salesOrderService.unapprove(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_REJECT)
    @PostMapping("/{id}/reject")
    @OperationLog(module = "sales", operation = "reject", message = "驳回销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> reject(@PathVariable Long id, @Valid @RequestBody SalesOrderRejectRequest request) {
        return ApiResponse.success(salesOrderService.reject(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_ORDER_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "sales", operation = "cancel", message = "取消销售订单", bizNo = "#result.data.orderNo")
    public ApiResponse<SalesOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(salesOrderService.cancel(id));
    }
}

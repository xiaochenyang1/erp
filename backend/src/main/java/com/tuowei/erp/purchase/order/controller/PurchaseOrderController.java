package com.tuowei.erp.purchase.order.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.log.annotation.OperationLog;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/purchase/orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_CREATE)
    @PostMapping
    @OperationLog(module = "purchase", operation = "create", message = "创建采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderCreateRequest request) {
        return ApiResponse.success(purchaseOrderService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PurchaseOrderResponse>> list(PurchaseOrderPageQuery query) {
        return ApiResponse.success(purchaseOrderService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_UPDATE)
    @PutMapping("/{id}")
    @OperationLog(module = "purchase", operation = "update", message = "更新采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        return ApiResponse.success(purchaseOrderService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_SUBMIT)
    @PostMapping("/{id}/submit")
    @OperationLog(module = "purchase", operation = "submit", message = "提交采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> submit(@PathVariable Long id, @Valid @RequestBody(required = false) PurchaseOrderSubmitRequest request) {
        PurchaseOrderSubmitRequest safeRequest = request == null ? new PurchaseOrderSubmitRequest(null) : request;
        return ApiResponse.success(purchaseOrderService.submit(id, safeRequest));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_APPROVE)
    @PostMapping("/{id}/approve")
    @OperationLog(module = "purchase", operation = "approve", message = "审核通过采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> approve(@PathVariable Long id, @Valid @RequestBody(required = false) PurchaseOrderApproveRequest request) {
        PurchaseOrderApproveRequest safeRequest = request == null ? new PurchaseOrderApproveRequest(null) : request;
        return ApiResponse.success(purchaseOrderService.approve(id, safeRequest));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_UNAPPROVE)
    @PostMapping("/{id}/unapprove")
    @OperationLog(module = "purchase", operation = "unapprove", message = "反审核采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> unapprove(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.unapprove(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_REJECT)
    @PostMapping("/{id}/reject")
    @OperationLog(module = "purchase", operation = "reject", message = "驳回采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> reject(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRejectRequest request) {
        return ApiResponse.success(purchaseOrderService.reject(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "purchase", operation = "cancel", message = "取消采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_CLOSE)
    @PostMapping("/{id}/close")
    @OperationLog(module = "purchase", operation = "close", message = "关闭采购订单", bizNo = "#result.data.orderNo")
    public ApiResponse<PurchaseOrderResponse> close(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.close(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_VIEW)
    @GetMapping("/{id}/trace")
    public ApiResponse<PurchaseOrderTraceResponse> trace(@PathVariable Long id) {
        return ApiResponse.success(purchaseOrderService.trace(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(PurchaseOrderPageQuery query) {
        return csv("purchase-orders.csv", purchaseOrderService.exportOrders(query));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "purchase-orders.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

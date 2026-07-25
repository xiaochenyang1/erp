package com.tuowei.erp.purchase.requisition.controller;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse; import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionService;
import com.tuowei.erp.purchase.requisition.web.*;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/purchase/requisitions")
public class PurchaseRequisitionController {
    private final PurchaseRequisitionService service;
    public PurchaseRequisitionController(PurchaseRequisitionService service){ this.service=service; }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @OperationLog(module="purchase", operation="requisition-create", bizNo="#result.data.requisitionNo")
    @PostMapping public ApiResponse<PurchaseRequisitionResponse> create(@Valid @RequestBody PurchaseRequisitionCreateRequest request){ return ApiResponse.success(service.create(request)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_VIEW) @GetMapping public ApiResponse<PageResponse<PurchaseRequisitionResponse>> list(PurchaseRequisitionPageQuery query){ return ApiResponse.success(service.list(query)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_VIEW) @GetMapping("/{id}") public ApiResponse<PurchaseRequisitionResponse> detail(@PathVariable Long id){ return ApiResponse.success(service.getById(id)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PutMapping("/{id}") public ApiResponse<PurchaseRequisitionResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseRequisitionUpdateRequest request){ return ApiResponse.success(service.update(id, request)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PostMapping("/{id}/submit") public ApiResponse<PurchaseRequisitionResponse> submit(@PathVariable Long id){ return ApiResponse.success(service.submit(id)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PostMapping("/{id}/approve") public ApiResponse<PurchaseRequisitionResponse> approve(@PathVariable Long id){ return ApiResponse.success(service.approve(id)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PostMapping("/{id}/reject") public ApiResponse<PurchaseRequisitionResponse> reject(@PathVariable Long id){ return ApiResponse.success(service.reject(id)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PostMapping("/{id}/cancel") public ApiResponse<PurchaseRequisitionResponse> cancel(@PathVariable Long id){ return ApiResponse.success(service.cancel(id)); }
    @PreAuthorize(PermissionCodes.HAS_PURCHASE_REQUISITION_MANAGE) @PostMapping("/{id}/convert-to-purchase-order") public ApiResponse<PurchaseRequisitionResponse> convert(@PathVariable Long id){ return ApiResponse.success(service.convertToPurchaseOrder(id)); }
}

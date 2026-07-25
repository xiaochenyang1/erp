package com.tuowei.erp.purchase.inquiry.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.inquiry.service.PurchaseInquiryService;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryCreateRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPageQuery;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryPoPrefillResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryResponse;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquirySelectQuoteRequest;
import com.tuowei.erp.purchase.inquiry.web.PurchaseInquiryUpdateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
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
@RequestMapping("/api/purchase/inquiries")
public class PurchaseInquiryController {

    private final PurchaseInquiryService purchaseInquiryService;

    public PurchaseInquiryController(PurchaseInquiryService purchaseInquiryService) {
        this.purchaseInquiryService = purchaseInquiryService;
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-create", bizNo = "#result.data.inquiryNo")
    @PostMapping
    public ApiResponse<PurchaseInquiryResponse> create(@Valid @RequestBody PurchaseInquiryCreateRequest request) {
        return ApiResponse.success(purchaseInquiryService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PurchaseInquiryResponse>> list(PurchaseInquiryPageQuery query) {
        return ApiResponse.success(purchaseInquiryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PurchaseInquiryResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseInquiryService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-update", bizNo = "#result.data.inquiryNo")
    @PutMapping("/{id}")
    public ApiResponse<PurchaseInquiryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInquiryUpdateRequest request
    ) {
        return ApiResponse.success(purchaseInquiryService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-submit", bizNo = "#result.data.inquiryNo")
    @PostMapping("/{id}/submit")
    public ApiResponse<PurchaseInquiryResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(purchaseInquiryService.submit(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-add-quote", bizNo = "#result.data.inquiryNo")
    @PostMapping("/{id}/quotes")
    public ApiResponse<PurchaseInquiryResponse> addQuote(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInquiryQuoteRequest request
    ) {
        return ApiResponse.success(purchaseInquiryService.addQuote(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-select-quote", bizNo = "#result.data.inquiryNo")
    @PostMapping("/{id}/select-quote")
    public ApiResponse<PurchaseInquiryResponse> selectQuote(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseInquirySelectQuoteRequest request
    ) {
        return ApiResponse.success(purchaseInquiryService.selectQuote(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_VIEW)
    @GetMapping("/{id}/po-prefill")
    public ApiResponse<PurchaseInquiryPoPrefillResponse> poPrefill(@PathVariable Long id) {
        return ApiResponse.success(purchaseInquiryService.poPrefill(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE + " and "
            + PermissionCodes.HAS_PURCHASE_ORDER_CREATE)
    @OperationLog(
            module = "purchase",
            operation = "inquiry-convert-to-purchase-order",
            message = "询价单转换采购订单",
            bizNo = "#result.data.orderNo"
    )
    @PostMapping("/{id}/convert-to-purchase-order")
    public ApiResponse<PurchaseOrderResponse> convertToPurchaseOrder(@PathVariable Long id) {
        return ApiResponse.success(purchaseInquiryService.convertToPurchaseOrder(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_INQUIRY_MANAGE)
    @OperationLog(module = "purchase", operation = "inquiry-cancel", bizNo = "#result.data.inquiryNo")
    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseInquiryResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(purchaseInquiryService.cancel(id));
    }
}

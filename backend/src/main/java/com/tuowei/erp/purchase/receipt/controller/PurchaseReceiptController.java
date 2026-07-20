package com.tuowei.erp.purchase.receipt.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
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
@RequestMapping("/api/purchase/receipts")
public class PurchaseReceiptController {

    private final PurchaseReceiptService purchaseReceiptService;

    public PurchaseReceiptController(PurchaseReceiptService purchaseReceiptService) {
        this.purchaseReceiptService = purchaseReceiptService;
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_CREATE)
    @OperationLog(module = "purchase", operation = "create", bizNo = "#result.data.receiptNo")
    @PostMapping
    public ApiResponse<PurchaseReceiptResponse> create(@Valid @RequestBody PurchaseReceiptCreateRequest request) {
        return ApiResponse.success(purchaseReceiptService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PurchaseReceiptResponse>> list(PurchaseReceiptPageQuery query) {
        return ApiResponse.success(purchaseReceiptService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(PurchaseReceiptPageQuery query) {
        return csv("purchase-receipts.csv", purchaseReceiptService.exportReceipts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PurchaseReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseReceiptService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_UPDATE)
    @OperationLog(module = "purchase", operation = "update", bizNo = "#result.data.receiptNo")
    @PutMapping("/{id}")
    public ApiResponse<PurchaseReceiptResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseReceiptUpdateRequest request) {
        return ApiResponse.success(purchaseReceiptService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_CANCEL)
    @OperationLog(module = "purchase", operation = "cancel", bizNo = "#result.data.receiptNo")
    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseReceiptResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(purchaseReceiptService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RECEIPT_POST)
    @OperationLog(module = "purchase", operation = "post", bizNo = "#result.data.receiptNo")
    @PostMapping("/{id}/post")
    public ApiResponse<PurchaseReceiptResponse> post(@PathVariable Long id) {
        return ApiResponse.success(purchaseReceiptService.post(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "purchase-receipts.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

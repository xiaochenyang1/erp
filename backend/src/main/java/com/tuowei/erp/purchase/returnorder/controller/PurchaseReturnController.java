package com.tuowei.erp.purchase.returnorder.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnCreateRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnUpdateRequest;
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
@RequestMapping("/api/purchase/returns")
public class PurchaseReturnController {

    private final PurchaseReturnService purchaseReturnService;

    public PurchaseReturnController(PurchaseReturnService purchaseReturnService) {
        this.purchaseReturnService = purchaseReturnService;
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_CREATE)
    @OperationLog(module = "purchase", operation = "create", bizNo = "#result.data.returnNo")
    @PostMapping
    public ApiResponse<PurchaseReturnResponse> create(@Valid @RequestBody PurchaseReturnCreateRequest request) {
        return ApiResponse.success(purchaseReturnService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PurchaseReturnResponse>> list(PurchaseReturnPageQuery query) {
        return ApiResponse.success(purchaseReturnService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(PurchaseReturnPageQuery query) {
        return csv("purchase-returns.csv", purchaseReturnService.exportReturns(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PurchaseReturnResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchaseReturnService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_UPDATE)
    @OperationLog(module = "purchase", operation = "update", bizNo = "#result.data.returnNo")
    @PutMapping("/{id}")
    public ApiResponse<PurchaseReturnResponse> update(@PathVariable Long id, @Valid @RequestBody PurchaseReturnUpdateRequest request) {
        return ApiResponse.success(purchaseReturnService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_CANCEL)
    @OperationLog(module = "purchase", operation = "cancel", bizNo = "#result.data.returnNo")
    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseReturnResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(purchaseReturnService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_RETURN_POST)
    @OperationLog(module = "purchase", operation = "post", bizNo = "#result.data.returnNo")
    @PostMapping("/{id}/post")
    public ApiResponse<PurchaseReturnResponse> post(@PathVariable Long id) {
        return ApiResponse.success(purchaseReturnService.post(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "purchase-returns.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

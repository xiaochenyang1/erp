package com.tuowei.erp.purchase.price.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.price.service.PurchasePriceService;
import com.tuowei.erp.purchase.price.web.PurchasePriceCreateRequest;
import com.tuowei.erp.purchase.price.web.PurchasePricePageQuery;
import com.tuowei.erp.purchase.price.web.PurchasePriceResolveResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceResponse;
import com.tuowei.erp.purchase.price.web.PurchasePriceUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchase/prices")
public class PurchasePriceController {

    private final PurchasePriceService purchasePriceService;

    public PurchasePriceController(PurchasePriceService purchasePriceService) {
        this.purchasePriceService = purchasePriceService;
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_MANAGE)
    @OperationLog(module = "purchase", operation = "price-create", bizNo = "#result.data.id")
    @PostMapping
    public ApiResponse<PurchasePriceResponse> create(@Valid @RequestBody PurchasePriceCreateRequest request) {
        return ApiResponse.success(purchasePriceService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PurchasePriceResponse>> list(PurchasePricePageQuery query) {
        return ApiResponse.success(purchasePriceService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_VIEW)
    @GetMapping("/resolve")
    public ApiResponse<PurchasePriceResolveResponse> resolve(
            @RequestParam Long productId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate
    ) {
        return ApiResponse.success(purchasePriceService.resolve(supplierId, productId, bizDate));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PurchasePriceResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(purchasePriceService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_MANAGE)
    @OperationLog(module = "purchase", operation = "price-update", bizNo = "#id")
    @PutMapping("/{id}")
    public ApiResponse<PurchasePriceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PurchasePriceUpdateRequest request
    ) {
        return ApiResponse.success(purchasePriceService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_MANAGE)
    @OperationLog(module = "purchase", operation = "price-enable", bizNo = "#id")
    @PostMapping("/{id}/enable")
    public ApiResponse<PurchasePriceResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(purchasePriceService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_PURCHASE_PRICE_MANAGE)
    @OperationLog(module = "purchase", operation = "price-disable", bizNo = "#id")
    @PostMapping("/{id}/disable")
    public ApiResponse<PurchasePriceResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(purchasePriceService.disable(id));
    }
}

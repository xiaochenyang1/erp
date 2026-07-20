package com.tuowei.erp.sales.price.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.price.service.SalesPriceService;
import com.tuowei.erp.sales.price.web.SalesPriceCreateRequest;
import com.tuowei.erp.sales.price.web.SalesPricePageQuery;
import com.tuowei.erp.sales.price.web.SalesPriceResolveResponse;
import com.tuowei.erp.sales.price.web.SalesPriceResponse;
import com.tuowei.erp.sales.price.web.SalesPriceUpdateRequest;
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
@RequestMapping("/api/sales/prices")
public class SalesPriceController {

    private final SalesPriceService salesPriceService;

    public SalesPriceController(SalesPriceService salesPriceService) {
        this.salesPriceService = salesPriceService;
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_MANAGE)
    @OperationLog(module = "sales", operation = "price-create", bizNo = "#result.data.id")
    @PostMapping
    public ApiResponse<SalesPriceResponse> create(@Valid @RequestBody SalesPriceCreateRequest request) {
        return ApiResponse.success(salesPriceService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SalesPriceResponse>> list(SalesPricePageQuery query) {
        return ApiResponse.success(salesPriceService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_VIEW)
    @GetMapping("/resolve")
    public ApiResponse<SalesPriceResolveResponse> resolve(
            @RequestParam Long productId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate
    ) {
        return ApiResponse.success(salesPriceService.resolve(customerId, productId, bizDate));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SalesPriceResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesPriceService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_MANAGE)
    @OperationLog(module = "sales", operation = "price-update", bizNo = "#id")
    @PutMapping("/{id}")
    public ApiResponse<SalesPriceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SalesPriceUpdateRequest request
    ) {
        return ApiResponse.success(salesPriceService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_MANAGE)
    @OperationLog(module = "sales", operation = "price-enable", bizNo = "#id")
    @PostMapping("/{id}/enable")
    public ApiResponse<SalesPriceResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(salesPriceService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_PRICE_MANAGE)
    @OperationLog(module = "sales", operation = "price-disable", bizNo = "#id")
    @PostMapping("/{id}/disable")
    public ApiResponse<SalesPriceResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(salesPriceService.disable(id));
    }
}

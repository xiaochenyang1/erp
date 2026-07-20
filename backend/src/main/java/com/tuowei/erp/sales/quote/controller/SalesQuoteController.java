package com.tuowei.erp.sales.quote.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.quote.service.SalesQuoteService;
import com.tuowei.erp.sales.quote.web.SalesQuoteConvertRequest;
import com.tuowei.erp.sales.quote.web.SalesQuotePageQuery;
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
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
@RequestMapping("/api/sales/quotes")
public class SalesQuoteController {

    private final SalesQuoteService salesQuoteService;

    public SalesQuoteController(SalesQuoteService salesQuoteService) {
        this.salesQuoteService = salesQuoteService;
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_MANAGE)
    @PostMapping
    @OperationLog(module = "sales", operation = "quote-create", bizNo = "#result.data.quoteNo")
    public ApiResponse<SalesQuoteResponse> create(@Valid @RequestBody SalesQuoteSaveRequest request) {
        return ApiResponse.success(salesQuoteService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SalesQuoteResponse>> list(SalesQuotePageQuery query) {
        return ApiResponse.success(salesQuoteService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SalesQuoteResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesQuoteService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<SalesQuoteResponse> update(@PathVariable Long id, @Valid @RequestBody SalesQuoteSaveRequest request) {
        return ApiResponse.success(salesQuoteService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_MANAGE)
    @PostMapping("/{id}/confirm")
    public ApiResponse<SalesQuoteResponse> confirm(@PathVariable Long id) {
        return ApiResponse.success(salesQuoteService.confirm(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_MANAGE)
    @PostMapping("/{id}/cancel")
    public ApiResponse<SalesQuoteResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(salesQuoteService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_QUOTE_MANAGE)
    @PostMapping("/{id}/convert-to-order")
    public ApiResponse<SalesOrderResponse> convert(
            @PathVariable Long id,
            @Valid @RequestBody SalesQuoteConvertRequest request
    ) {
        return ApiResponse.success(salesQuoteService.convertToOrder(id, request.warehouseId()));
    }
}

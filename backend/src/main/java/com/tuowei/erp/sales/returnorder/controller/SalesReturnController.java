package com.tuowei.erp.sales.returnorder.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnUpdateRequest;
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
@RequestMapping("/api/sales/returns")
public class SalesReturnController {

    private final SalesReturnService salesReturnService;

    public SalesReturnController(SalesReturnService salesReturnService) {
        this.salesReturnService = salesReturnService;
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_CREATE)
    @PostMapping
    @OperationLog(module = "sales", operation = "create-return", message = "创建销售退货单", bizNo = "#result.data.returnNo")
    public ApiResponse<SalesReturnResponse> create(@Valid @RequestBody SalesReturnCreateRequest request) {
        return ApiResponse.success(salesReturnService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SalesReturnResponse>> list(SalesReturnPageQuery query) {
        return ApiResponse.success(salesReturnService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SalesReturnResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(salesReturnService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_UPDATE)
    @PutMapping("/{id}")
    @OperationLog(module = "sales", operation = "update-return", message = "更新销售退货单", bizNo = "#result.data.returnNo")
    public ApiResponse<SalesReturnResponse> update(@PathVariable Long id, @Valid @RequestBody SalesReturnUpdateRequest request) {
        return ApiResponse.success(salesReturnService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "sales", operation = "cancel-return", message = "取消销售退货单", bizNo = "#result.data.returnNo")
    public ApiResponse<SalesReturnResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(salesReturnService.cancel(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SALES_RETURN_POST)
    @PostMapping("/{id}/post")
    @OperationLog(module = "sales", operation = "post-return", message = "过账销售退货单", bizNo = "#result.data.returnNo")
    public ApiResponse<SalesReturnResponse> post(@PathVariable Long id) {
        return ApiResponse.success(salesReturnService.post(id));
    }
}

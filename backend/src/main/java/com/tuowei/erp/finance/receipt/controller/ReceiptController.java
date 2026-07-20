package com.tuowei.erp.finance.receipt.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptPageQuery;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_CREATE)
    @PostMapping
    @OperationLog(module = "finance", operation = "create-receipt", message = "创建收款单", bizNo = "#result.data.receiptNo")
    public ApiResponse<ReceiptResponse> create(@Valid @RequestBody ReceiptCreateRequest request) {
        return ApiResponse.success(receiptService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "finance", operation = "cancel-receipt", message = "作废收款单", bizNo = "#id")
    public ApiResponse<ReceiptResponse> cancel(@PathVariable Long id, @Valid @RequestBody ReceiptCancelRequest request) {
        return ApiResponse.success(receiptService.cancel(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ReceiptResponse>> list(ReceiptPageQuery query) {
        return ApiResponse.success(receiptService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_RECEIPT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ReceiptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(receiptService.detail(id));
    }
}

package com.tuowei.erp.finance.invoice.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.invoice.service.FinanceInvoiceService;
import com.tuowei.erp.finance.invoice.web.InvoiceCreateRequest;
import com.tuowei.erp.finance.invoice.web.InvoicePageQuery;
import com.tuowei.erp.finance.invoice.web.InvoiceResponse;
import com.tuowei.erp.finance.invoice.web.InvoiceUpdateRequest;
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
@RequestMapping("/api/finance/invoices")
public class FinanceInvoiceController {

    private final FinanceInvoiceService financeInvoiceService;

    public FinanceInvoiceController(FinanceInvoiceService financeInvoiceService) {
        this.financeInvoiceService = financeInvoiceService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_MANAGE)
    @PostMapping
    @OperationLog(module = "finance", operation = "create-invoice", message = "创建发票登记", bizNo = "#result.data.invoiceNo")
    public ApiResponse<InvoiceResponse> create(@Valid @RequestBody InvoiceCreateRequest request) {
        return ApiResponse.success(financeInvoiceService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<InvoiceResponse>> list(InvoicePageQuery query) {
        return ApiResponse.success(financeInvoiceService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<InvoiceResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(financeInvoiceService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_MANAGE)
    @PutMapping("/{id}")
    @OperationLog(module = "finance", operation = "update-invoice", message = "更新发票登记", bizNo = "#id")
    public ApiResponse<InvoiceResponse> update(@PathVariable Long id, @Valid @RequestBody InvoiceUpdateRequest request) {
        return ApiResponse.success(financeInvoiceService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_MANAGE)
    @PostMapping("/{id}/post")
    @OperationLog(module = "finance", operation = "post-invoice", message = "确认发票登记", bizNo = "#id")
    public ApiResponse<InvoiceResponse> post(@PathVariable Long id) {
        return ApiResponse.success(financeInvoiceService.post(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_INVOICE_MANAGE)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "finance", operation = "cancel-invoice", message = "作废发票登记", bizNo = "#id")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(financeInvoiceService.cancel(id));
    }
}

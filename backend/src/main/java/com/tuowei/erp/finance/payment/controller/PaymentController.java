package com.tuowei.erp.finance.payment.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.payment.web.PaymentPageQuery;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
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
@RequestMapping("/api/finance/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_CREATE)
    @PostMapping
    @OperationLog(module = "finance", operation = "create-payment", message = "创建付款单", bizNo = "#result.data.paymentNo")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody PaymentCreateRequest request) {
        return ApiResponse.success(paymentService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_CANCEL)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "finance", operation = "cancel-payment", message = "作废付款单", bizNo = "#id")
    public ApiResponse<PaymentResponse> cancel(@PathVariable Long id, @Valid @RequestBody PaymentCancelRequest request) {
        return ApiResponse.success(paymentService.cancel(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PaymentResponse>> list(PaymentPageQuery query) {
        return ApiResponse.success(paymentService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PAYMENT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(paymentService.detail(id));
    }
}

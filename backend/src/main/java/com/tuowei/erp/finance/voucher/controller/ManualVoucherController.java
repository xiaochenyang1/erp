package com.tuowei.erp.finance.voucher.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.voucher.service.ManualVoucherService;
import com.tuowei.erp.finance.voucher.web.ManualVoucherCancelRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.ManualVoucherRejectRequest;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手工凭证：财务人员手工录入的记账凭证。写操作挂在 /api/finance/vouchers/manual 下，
 * 与只读的自动凭证查询（{@link VoucherController}）区分。状态机：
 * DRAFT →(submit)→ PENDING →(approve)→ APPROVED →(post)→ POSTED；PENDING →(reject)→ DRAFT；
 * POSTED →(cancel/reversal)→ CANCELLED。
 */
@RestController
@RequestMapping("/api/finance/vouchers/manual")
public class ManualVoucherController {

    private final ManualVoucherService manualVoucherService;

    public ManualVoucherController(ManualVoucherService manualVoucherService) {
        this.manualVoucherService = manualVoucherService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ManualVoucherResponse>> list(ManualVoucherPageQuery query) {
        return ApiResponse.success(manualVoucherService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ManualVoucherResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(manualVoucherService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_MANAGE)
    @PostMapping
    public ApiResponse<ManualVoucherResponse> create(@Valid @RequestBody ManualVoucherSaveRequest request) {
        return ApiResponse.success(manualVoucherService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<ManualVoucherResponse> update(@PathVariable Long id, @Valid @RequestBody ManualVoucherSaveRequest request) {
        return ApiResponse.success(manualVoucherService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_MANAGE)
    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        manualVoucherService.submit(id);
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_APPROVE)
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        manualVoucherService.approve(id);
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_APPROVE)
    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @Valid @RequestBody ManualVoucherRejectRequest request) {
        manualVoucherService.reject(id, request.reason());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_POST)
    @PostMapping("/{id}/post")
    public ApiResponse<Void> post(@PathVariable Long id) {
        manualVoucherService.post(id);
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_POST)
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @Valid @RequestBody ManualVoucherCancelRequest request) {
        manualVoucherService.cancel(id, request.reason());
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_MANAGE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        manualVoucherService.delete(id);
        return ApiResponse.success(null);
    }
}

package com.tuowei.erp.finance.voucher.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import com.tuowei.erp.finance.voucher.web.VoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.VoucherResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/vouchers")
public class VoucherController {

    private final VoucherQueryService voucherQueryService;

    public VoucherController(VoucherQueryService voucherQueryService) {
        this.voucherQueryService = voucherQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<VoucherResponse>> list(VoucherPageQuery query) {
        return ApiResponse.success(voucherQueryService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(voucherQueryService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_VOUCHER_VIEW)
    @GetMapping("/{id}/entries")
    public ApiResponse<List<VoucherEntryResponse>> entries(@PathVariable Long id) {
        return ApiResponse.success(voucherQueryService.entries(id));
    }
}

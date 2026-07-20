package com.tuowei.erp.finance.margin.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.margin.service.GrossMarginService;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/gross-margin")
public class GrossMarginController {

    private final GrossMarginService grossMarginService;

    public GrossMarginController(GrossMarginService grossMarginService) {
        this.grossMarginService = grossMarginService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_MARGIN_VIEW)
    @GetMapping
    public ApiResponse<GrossMarginSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ApiResponse.success(grossMarginService.summary(dateFrom, dateTo));
    }
}

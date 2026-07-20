package com.tuowei.erp.finance.aging.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.aging.service.FinanceAgingService;
import com.tuowei.erp.finance.aging.web.FinanceAgingSummaryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/aging")
public class FinanceAgingController {

    private final FinanceAgingService financeAgingService;

    public FinanceAgingController(FinanceAgingService financeAgingService) {
        this.financeAgingService = financeAgingService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_AGING_VIEW)
    @GetMapping
    public ApiResponse<FinanceAgingSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return ApiResponse.success(financeAgingService.summary(asOfDate));
    }
}

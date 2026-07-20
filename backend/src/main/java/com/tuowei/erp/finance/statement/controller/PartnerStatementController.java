package com.tuowei.erp.finance.statement.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.statement.service.PartnerStatementService;
import com.tuowei.erp.finance.statement.web.PartnerStatementResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/statements")
public class PartnerStatementController {

    private final PartnerStatementService partnerStatementService;

    public PartnerStatementController(PartnerStatementService partnerStatementService) {
        this.partnerStatementService = partnerStatementService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_STATEMENT_VIEW)
    @GetMapping
    public ApiResponse<PartnerStatementResponse> statement(
            @RequestParam String partnerType,
            @RequestParam Long partnerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ApiResponse.success(partnerStatementService.statement(partnerType, partnerId, dateFrom, dateTo));
    }
}

package com.tuowei.erp.finance.financialstatement.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.financialstatement.service.FinancialStatementService;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetQuery;
import com.tuowei.erp.finance.financialstatement.web.BalanceSheetResponse;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementQuery;
import com.tuowei.erp.finance.financialstatement.web.IncomeStatementResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/financial-statements")
public class FinancialStatementController {

    private final FinancialStatementService financialStatementService;

    public FinancialStatementController(FinancialStatementService financialStatementService) {
        this.financialStatementService = financialStatementService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/balance-sheet")
    public ApiResponse<BalanceSheetResponse> balanceSheet(BalanceSheetQuery query) {
        return ApiResponse.success(financialStatementService.balanceSheet(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_LEDGER_VIEW)
    @GetMapping("/income-statement")
    public ApiResponse<IncomeStatementResponse> incomeStatement(IncomeStatementQuery query) {
        return ApiResponse.success(financialStatementService.incomeStatement(query));
    }
}

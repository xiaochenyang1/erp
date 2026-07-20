package com.tuowei.erp.finance.fund.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.fund.service.FundService;
import com.tuowei.erp.finance.fund.web.BankStatementCreateRequest;
import com.tuowei.erp.finance.fund.web.BankStatementMatchRequest;
import com.tuowei.erp.finance.fund.web.BankStatementPageQuery;
import com.tuowei.erp.finance.fund.web.BankStatementResponse;
import com.tuowei.erp.finance.fund.web.BankStatementUnmatchRequest;
import com.tuowei.erp.finance.fund.web.FundAccountCreateRequest;
import com.tuowei.erp.finance.fund.web.FundAccountPageQuery;
import com.tuowei.erp.finance.fund.web.FundAccountResponse;
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
@RequestMapping("/api/finance/fund")
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_MANAGE)
    @PostMapping("/accounts")
    @OperationLog(module = "finance", operation = "create-fund-account", message = "创建资金账户", bizNo = "#result.data.accountCode")
    public ApiResponse<FundAccountResponse> createAccount(@Valid @RequestBody FundAccountCreateRequest request) {
        return ApiResponse.success(fundService.createAccount(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/accounts")
    public ApiResponse<PageResponse<FundAccountResponse>> listAccounts(FundAccountPageQuery query) {
        return ApiResponse.success(fundService.listAccounts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/accounts/{id}")
    public ApiResponse<FundAccountResponse> accountDetail(@PathVariable Long id) {
        return ApiResponse.success(fundService.accountDetail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_MANAGE)
    @PostMapping("/statements")
    @OperationLog(module = "finance", operation = "create-bank-statement", message = "创建银行流水", bizNo = "#result.data.statementNo")
    public ApiResponse<BankStatementResponse> createStatement(@Valid @RequestBody BankStatementCreateRequest request) {
        return ApiResponse.success(fundService.createStatement(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/statements")
    public ApiResponse<PageResponse<BankStatementResponse>> listStatements(BankStatementPageQuery query) {
        return ApiResponse.success(fundService.listStatements(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_VIEW)
    @GetMapping("/statements/{id}")
    public ApiResponse<BankStatementResponse> statementDetail(@PathVariable Long id) {
        return ApiResponse.success(fundService.statementDetail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_RECONCILE)
    @PostMapping("/statements/{id}/match")
    @OperationLog(module = "finance", operation = "match-bank-statement", message = "匹配银行流水", bizNo = "#id")
    public ApiResponse<BankStatementResponse> matchStatement(
            @PathVariable Long id,
            @Valid @RequestBody BankStatementMatchRequest request
    ) {
        return ApiResponse.success(fundService.matchStatement(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_FUND_RECONCILE)
    @PostMapping("/statements/{id}/unmatch")
    @OperationLog(module = "finance", operation = "unmatch-bank-statement", message = "取消匹配银行流水", bizNo = "#id")
    public ApiResponse<BankStatementResponse> unmatchStatement(
            @PathVariable Long id,
            @Valid @RequestBody BankStatementUnmatchRequest request
    ) {
        return ApiResponse.success(fundService.unmatchStatement(id, request));
    }
}

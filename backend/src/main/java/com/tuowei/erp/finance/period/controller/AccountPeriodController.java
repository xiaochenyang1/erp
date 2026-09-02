package com.tuowei.erp.finance.period.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodCloseChecker;
import com.tuowei.erp.finance.period.service.AccountPeriodService;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodGenerateRequest;
import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/periods")
public class AccountPeriodController {

    private final AccountPeriodService accountPeriodService;
    private final AccountPeriodCloseChecker closeChecker;
    private final InventoryFinanceReconciliationService reconciliationService;

    public AccountPeriodController(
            AccountPeriodService accountPeriodService,
            AccountPeriodCloseChecker closeChecker,
            InventoryFinanceReconciliationService reconciliationService
    ) {
        this.accountPeriodService = accountPeriodService;
        this.closeChecker = closeChecker;
        this.reconciliationService = reconciliationService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_MANAGE)
    @PostMapping("/generate")
    @OperationLog(module = "finance", operation = "generate-account-period", message = "生成会计期间", bizNo = "#request.year")
    public ApiResponse<List<AccountPeriodResponse>> generate(@Valid @RequestBody AccountPeriodGenerateRequest request) {
        return ApiResponse.success(accountPeriodService.generate(request.year()));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping
    public ApiResponse<List<AccountPeriodResponse>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.success(accountPeriodService.list(year));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_CLOSE)
    @PostMapping("/{id}/lock")
    @OperationLog(module = "finance", operation = "lock-account-period", message = "锁定会计期间", bizNo = "#id")
    public ApiResponse<AccountPeriodResponse> lock(@PathVariable Long id) {
        return ApiResponse.success(accountPeriodService.lock(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping("/{id}/close-check")
    public ApiResponse<AccountPeriodCloseCheckResponse> closeCheck(@PathVariable Long id) {
        return ApiResponse.success(closeChecker.check(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_CLOSE)
    @PostMapping("/{id}/close")
    @OperationLog(module = "finance", operation = "close-account-period", message = "结账会计期间", bizNo = "#id")
    public ApiResponse<AccountPeriodResponse> close(@PathVariable Long id) {
        return ApiResponse.success(accountPeriodService.close(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_REOPEN)
    @PostMapping("/{id}/reopen")
    @OperationLog(module = "finance", operation = "reopen-account-period", message = "解锁会计期间", bizNo = "#id")
    public ApiResponse<AccountPeriodResponse> reopen(@PathVariable Long id) {
        return ApiResponse.success(accountPeriodService.reopen(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping("/{id}/close-snapshots")
    public ApiResponse<List<AccountPeriodCloseSnapshotResponse>> closeSnapshots(@PathVariable Long id) {
        return ApiResponse.success(accountPeriodService.closeSnapshots(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping("/{id}/reconciliation")
    public ApiResponse<InventoryFinanceReconciliationResponse> reconciliation(@PathVariable Long id) {
        return ApiResponse.success(reconciliationService.summary(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping("/{id}/reconciliation/differences")
    public ApiResponse<List<InventoryFinanceDifferenceResponse>> reconciliationDifferences(
            @PathVariable Long id,
            InventoryFinanceDifferenceQuery query
    ) {
        return ApiResponse.success(reconciliationService.differences(id, query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_PERIOD_VIEW)
    @GetMapping("/{id}/reconciliation/differences/detail")
    public ApiResponse<InventoryFinanceDifferenceDetailResponse> reconciliationDifferenceDetail(
            @PathVariable Long id,
            @RequestParam String sourceType,
            @RequestParam String sourceNo
    ) {
        return ApiResponse.success(reconciliationService.differenceDetail(id, sourceType, sourceNo));
    }
}

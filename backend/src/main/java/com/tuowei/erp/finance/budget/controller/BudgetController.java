package com.tuowei.erp.finance.budget.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.budget.service.BudgetService;
import com.tuowei.erp.finance.budget.web.BudgetCreateRequest;
import com.tuowei.erp.finance.budget.web.BudgetExecutionQuery;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.budget.web.BudgetPageQuery;
import com.tuowei.erp.finance.budget.web.BudgetResponse;
import com.tuowei.erp.finance.budget.web.BudgetUpdateRequest;
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
@RequestMapping("/api/finance/budgets")
public class BudgetController {
    private final BudgetService budgetService;
    public BudgetController(BudgetService budgetService) { this.budgetService = budgetService; }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_VIEW)
    @GetMapping public ApiResponse<PageResponse<BudgetResponse>> list(BudgetPageQuery query) { return ApiResponse.success(budgetService.list(query)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_VIEW)
    @GetMapping("/{id}") public ApiResponse<BudgetResponse> detail(@PathVariable Long id) { return ApiResponse.success(budgetService.detail(id)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_VIEW)
    @GetMapping("/execution") public ApiResponse<BudgetExecutionResponse> execution(BudgetExecutionQuery query) { return ApiResponse.success(budgetService.execution(query)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_MANAGE)
    @PostMapping public ApiResponse<BudgetResponse> create(@Valid @RequestBody BudgetCreateRequest request) { return ApiResponse.success(budgetService.create(request)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_MANAGE)
    @PutMapping("/{id}") public ApiResponse<BudgetResponse> update(@PathVariable Long id, @Valid @RequestBody BudgetUpdateRequest request) { return ApiResponse.success(budgetService.update(id, request)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_MANAGE)
    @PostMapping("/{id}/submit") public ApiResponse<BudgetResponse> submit(@PathVariable Long id) { return ApiResponse.success(budgetService.submit(id)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_APPROVE)
    @PostMapping("/{id}/approve") public ApiResponse<BudgetResponse> approve(@PathVariable Long id) { return ApiResponse.success(budgetService.approve(id)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_MANAGE)
    @PostMapping("/{id}/close") public ApiResponse<BudgetResponse> close(@PathVariable Long id) { return ApiResponse.success(budgetService.close(id)); }
    @PreAuthorize(PermissionCodes.HAS_FINANCE_BUDGET_MANAGE)
    @PostMapping("/{id}/cancel") public ApiResponse<BudgetResponse> cancel(@PathVariable Long id) { return ApiResponse.success(budgetService.cancel(id)); }
}

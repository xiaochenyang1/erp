package com.tuowei.erp.finance.expense.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpenseApproveRequest;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.expense.web.ExpenseReconciliationResponse;
import com.tuowei.erp.finance.expense.web.ExpenseRejectRequest;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.expense.web.ExpenseSubmitRequest;
import com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
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
@RequestMapping("/api/finance/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping
    @OperationLog(module = "finance", operation = "create-expense", message = "创建费用单", bizNo = "#result.data.expenseNo")
    public ApiResponse<ExpenseResponse> create(@Valid @RequestBody ExpenseCreateRequest request) {
        return ApiResponse.success(expenseService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @GetMapping
    public ApiResponse<PageResponse<ExpenseResponse>> list(ExpensePageQuery query) {
        return ApiResponse.success(expenseService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @GetMapping("/{id}")
    public ApiResponse<ExpenseResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(expenseService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PutMapping("/{id}")
    @OperationLog(module = "finance", operation = "update-expense", message = "更新费用单", bizNo = "#id")
    public ApiResponse<ExpenseResponse> update(@PathVariable Long id, @Valid @RequestBody ExpenseUpdateRequest request) {
        return ApiResponse.success(expenseService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/submit")
    @OperationLog(module = "finance", operation = "submit-expense", message = "提交费用单审批", bizNo = "#id")
    public ApiResponse<ExpenseResponse> submit(@PathVariable Long id, @Valid @RequestBody(required = false) ExpenseSubmitRequest request) {
        return ApiResponse.success(expenseService.submit(id, request == null ? null : request.remark()));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/approve")
    @OperationLog(module = "finance", operation = "approve-expense", message = "审批通过费用单", bizNo = "#id")
    public ApiResponse<ExpenseResponse> approve(@PathVariable Long id, @Valid @RequestBody(required = false) ExpenseApproveRequest request) {
        return ApiResponse.success(expenseService.approve(id, request == null ? null : request.remark()));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/reject")
    @OperationLog(module = "finance", operation = "reject-expense", message = "驳回费用单", bizNo = "#id")
    public ApiResponse<ExpenseResponse> reject(@PathVariable Long id, @Valid @RequestBody ExpenseRejectRequest request) {
        return ApiResponse.success(expenseService.reject(id, request.reason()));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @GetMapping("/{id}/reconciliation")
    public ApiResponse<ExpenseReconciliationResponse> reconciliation(@PathVariable Long id) {
        return ApiResponse.success(expenseService.reconciliation(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/post")
    @OperationLog(module = "finance", operation = "post-expense", message = "费用单过账", bizNo = "#id")
    public ApiResponse<ExpenseResponse> post(@PathVariable Long id) {
        return ApiResponse.success(expenseService.post(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/reverse")
    @OperationLog(module = "finance", operation = "reverse-expense", message = "红冲费用单", bizNo = "#id")
    public ApiResponse<ExpenseResponse> reverse(@PathVariable Long id) {
        return ApiResponse.success(expenseService.reverse(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_EXPENSE_MANAGE)
    @PostMapping("/{id}/cancel")
    @OperationLog(module = "finance", operation = "cancel-expense", message = "作废费用单", bizNo = "#id")
    public ApiResponse<ExpenseResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(expenseService.cancel(id));
    }
}

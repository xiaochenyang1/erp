package com.tuowei.erp.finance.expense.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.expense.web.ExpenseReconciliationResponse;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for expense queries, commands and posting. */
@Service
public class ExpenseService {

    private final ExpenseQueryService expenseQueryService;
    private final ExpenseCommandService expenseCommandService;
    private final ExpensePostingService expensePostingService;

    public ExpenseService(
            ExpenseQueryService expenseQueryService,
            ExpenseCommandService expenseCommandService,
            ExpensePostingService expensePostingService
    ) {
        this.expenseQueryService = expenseQueryService;
        this.expenseCommandService = expenseCommandService;
        this.expensePostingService = expensePostingService;
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        return expenseCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(ExpensePageQuery query) {
        ExpensePageQuery safeQuery = query == null ? new ExpensePageQuery() : query;
        return expenseQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse detail(Long id) {
        return expenseQueryService.detail(id);
    }

    @Transactional(readOnly = true)
    public ExpenseReconciliationResponse reconciliation(Long id) {
        return expenseQueryService.reconciliation(id);
    }

    @Transactional
    public ExpenseResponse update(Long id, ExpenseUpdateRequest request) {
        return expenseCommandService.update(id, request);
    }

    @Transactional
    public ExpenseResponse submit(Long id, String remark) {
        return expenseCommandService.submit(id, remark);
    }

    @Transactional
    public ExpenseResponse approve(Long id, String remark) {
        return expenseCommandService.approve(id, remark);
    }

    @Transactional
    public ExpenseResponse approveWorkflowTask(Long taskId, Long id, String remark) {
        return expenseCommandService.approveWorkflowTask(taskId, id, remark);
    }

    @Transactional
    public ExpenseResponse reject(Long id, String reason) {
        return expenseCommandService.reject(id, reason);
    }

    @Transactional
    public ExpenseResponse rejectWorkflowTask(Long taskId, Long id, String reason) {
        return expenseCommandService.rejectWorkflowTask(taskId, id, reason);
    }

    @Transactional
    public ExpenseResponse post(Long id) {
        return expensePostingService.post(id);
    }

    @Transactional
    public ExpenseResponse reverse(Long id) {
        return expensePostingService.reverse(id);
    }

    @Transactional
    public ExpenseResponse cancel(Long id) {
        return expenseCommandService.cancel(id);
    }
}

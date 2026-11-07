package com.tuowei.erp.finance.budget.service;

import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Compatibility facade delegating budget-execution preview to the query service and expense budget state transitions to the command service. */
@Service
public class BudgetExecutionService {

    private final BudgetExecutionQueryService queryService;
    private final BudgetExecutionCommandService commandService;

    public BudgetExecutionService(BudgetExecutionQueryService queryService, BudgetExecutionCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional(readOnly = true)
    public BudgetExecutionResponse check(LocalDate expenseDate, Long deptId, Long subjectId, BigDecimal amount) {
        return queryService.check(expenseDate, deptId, subjectId, amount);
    }

    @Transactional
    public void commitExpense(ExpenseEntity expense) {
        commandService.commitExpense(expense);
    }

    @Transactional
    public void actualizeExpense(ExpenseEntity expense) {
        commandService.actualizeExpense(expense);
    }

    @Transactional
    public void releaseExpense(ExpenseEntity expense) {
        commandService.releaseExpense(expense);
    }

    @Transactional
    public void reverseExpense(ExpenseEntity expense) {
        commandService.reverseExpense(expense);
    }
}

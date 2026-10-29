package com.tuowei.erp.finance.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Write side of budget execution: commit/actualize/release/reverse expense amounts against budget lines with optimistic locking. */
@Service
public class BudgetExecutionCommandService {
    private static final String COMMITTED = "COMMITTED";
    private static final String ACTUAL = "ACTUAL";
    private static final String RELEASED = "RELEASED";
    private static final String REVERSED = "REVERSED";
    private static final String OPTIMISTIC_LOCK_MESSAGE = "预算执行数据已被其他操作修改，请刷新后重试";

    private final BudgetLineMapper budgetLineMapper;
    private final ExpenseMapper expenseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final BudgetExecutionQueryService queryService;

    public BudgetExecutionCommandService(BudgetLineMapper budgetLineMapper, ExpenseMapper expenseMapper, AuditMetadataFactory auditMetadataFactory, BudgetExecutionQueryService queryService) {
        this.budgetLineMapper = budgetLineMapper;
        this.expenseMapper = expenseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public void commitExpense(ExpenseEntity expense) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetEntity budget = queryService.activeBudget(audit, expense.getExpenseDate().getYear());
        if (COMMITTED.equals(expense.getBudgetState()) || ACTUAL.equals(expense.getBudgetState())) return;
        if (budget == null) {
            clearBudgetState(expense, audit);
            return;
        }
        BudgetLineEntity line = queryService.resolvedLine(audit, budget, expense.getExpenseDate(), expense.getDeptId(), expense.getSubjectId());
        BigDecimal amount = queryService.zero(expense.getAmount());
        BigDecimal available = line == null ? BigDecimal.ZERO
                : queryService.zero(line.getBudgetAmount()).subtract(queryService.zero(line.getCommittedAmount())).subtract(queryService.zero(line.getActualAmount()));
        boolean overrun = line == null || available.compareTo(amount) < 0;
        if (overrun && "REJECT".equals(budget.getControlPolicy())) {
            throw new IllegalArgumentException("费用提交后将超过预算可用额度，当前可用 " + available);
        }
        if (line != null) {
            line.setCommittedAmount(queryService.zero(line.getCommittedAmount()).add(amount));
            line.setUpdatedBy(audit.userId()); line.setUpdatedTime(audit.now());
            OptimisticLockGuard.requireUpdated(budgetLineMapper.updateById(line), OPTIMISTIC_LOCK_MESSAGE);
        }
        expense.setBudgetLineId(line == null ? null : line.getId());
        expense.setBudgetState(COMMITTED);
        expense.setBudgetOverrunFlag(overrun ? 1 : 0);
        expense.setUpdatedBy(audit.userId()); expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void actualizeExpense(ExpenseEntity expense) {
        if (ACTUAL.equals(expense.getBudgetState())) return;
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetLineEntity line = budgetLine(expense, audit);
        if (line != null) {
            BigDecimal amount = queryService.zero(expense.getAmount());
            line.setCommittedAmount(queryService.zero(line.getCommittedAmount()).subtract(amount).max(BigDecimal.ZERO));
            line.setActualAmount(queryService.zero(line.getActualAmount()).add(amount));
            line.setUpdatedBy(audit.userId()); line.setUpdatedTime(audit.now());
            OptimisticLockGuard.requireUpdated(budgetLineMapper.updateById(line), OPTIMISTIC_LOCK_MESSAGE);
        }
        expense.setBudgetState(ACTUAL); expense.setUpdatedBy(audit.userId()); expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void releaseExpense(ExpenseEntity expense) {
        if (!COMMITTED.equals(expense.getBudgetState())) return;
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetLineEntity line = budgetLine(expense, audit);
        if (line != null) {
            line.setCommittedAmount(queryService.zero(line.getCommittedAmount()).subtract(queryService.zero(expense.getAmount())).max(BigDecimal.ZERO));
            line.setUpdatedBy(audit.userId()); line.setUpdatedTime(audit.now());
            OptimisticLockGuard.requireUpdated(budgetLineMapper.updateById(line), OPTIMISTIC_LOCK_MESSAGE);
        }
        expense.setBudgetState(RELEASED); expense.setUpdatedBy(audit.userId()); expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void reverseExpense(ExpenseEntity expense) {
        if (!ACTUAL.equals(expense.getBudgetState())) return;
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetLineEntity line = budgetLine(expense, audit);
        if (line != null) {
            line.setActualAmount(queryService.zero(line.getActualAmount()).subtract(queryService.zero(expense.getAmount())).max(BigDecimal.ZERO));
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(audit.now());
            OptimisticLockGuard.requireUpdated(budgetLineMapper.updateById(line), OPTIMISTIC_LOCK_MESSAGE);
        }
        expense.setBudgetState(REVERSED);
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
    }

    private void clearBudgetState(ExpenseEntity expense, AuditMetadata audit) {
        if (expense.getBudgetLineId() == null
                && (expense.getBudgetState() == null || "NONE".equals(expense.getBudgetState()))
                && (expense.getBudgetOverrunFlag() == null || expense.getBudgetOverrunFlag() == 0)) {
            return;
        }
        expense.setBudgetLineId(null);
        expense.setBudgetState("NONE");
        expense.setBudgetOverrunFlag(0);
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
    }

    private BudgetLineEntity budgetLine(ExpenseEntity expense, AuditMetadata audit) {
        if (expense.getBudgetLineId() == null) return null;
        BudgetLineEntity line = budgetLineMapper.selectById(expense.getBudgetLineId());
        if (line == null || !audit.companyId().equals(line.getCompanyId()) || !audit.accountBookId().equals(line.getAccountBookId())) return null;
        return line;
    }
}

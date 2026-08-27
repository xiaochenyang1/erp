package com.tuowei.erp.finance.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class BudgetExecutionService {
    private static final String COMMITTED = "COMMITTED";
    private static final String ACTUAL = "ACTUAL";
    private static final String RELEASED = "RELEASED";
    private static final String REVERSED = "REVERSED";
    private static final String OPTIMISTIC_LOCK_MESSAGE = "预算执行数据已被其他操作修改，请刷新后重试";
    private final BudgetMapper budgetMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final ExpenseMapper expenseMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BudgetExecutionService(BudgetMapper budgetMapper, BudgetLineMapper budgetLineMapper, ExpenseMapper expenseMapper, AuditMetadataFactory auditMetadataFactory) {
        this.budgetMapper = budgetMapper; this.budgetLineMapper = budgetLineMapper; this.expenseMapper = expenseMapper; this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public BudgetExecutionResponse check(LocalDate expenseDate, Long deptId, Long subjectId, BigDecimal amount) {
        AuditMetadata audit = auditMetadataFactory.current();
        int year = expenseDate.getYear(); int month = expenseDate.getMonthValue();
        BudgetEntity budgetEntity = activeBudget(audit, year);
        BudgetLineEntity line = resolvedLine(audit, budgetEntity, expenseDate, deptId, subjectId);
        BigDecimal budget = line == null ? BigDecimal.ZERO : zero(line.getBudgetAmount());
        BigDecimal committed = line == null ? BigDecimal.ZERO : zero(line.getCommittedAmount());
        BigDecimal actual = line == null ? BigDecimal.ZERO : zero(line.getActualAmount());
        BigDecimal available = budget.subtract(committed).subtract(actual);
        BigDecimal requested = zero(amount);
        BigDecimal projected = available.subtract(requested);
        boolean overrun = budgetEntity != null && (line == null || projected.signum() < 0);
        String source = line == null ? "NONE" : line.getPeriodMonth() == 0 ? "ANNUAL" : "MONTHLY";
        return new BudgetExecutionResponse(year, month, deptId, subjectId, budget, committed, actual, available,
                budgetEntity == null ? null : budgetEntity.getId(), line == null ? null : line.getId(),
                budgetEntity == null ? null : budgetEntity.getControlPolicy(), source, requested, projected, overrun);
    }

    @Transactional
    public void commitExpense(ExpenseEntity expense) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetEntity budget = activeBudget(audit, expense.getExpenseDate().getYear());
        if (COMMITTED.equals(expense.getBudgetState()) || ACTUAL.equals(expense.getBudgetState())) return;
        if (budget == null) {
            clearBudgetState(expense, audit);
            return;
        }
        BudgetLineEntity line = resolvedLine(audit, budget, expense.getExpenseDate(), expense.getDeptId(), expense.getSubjectId());
        BigDecimal amount = zero(expense.getAmount());
        BigDecimal available = line == null ? BigDecimal.ZERO
                : zero(line.getBudgetAmount()).subtract(zero(line.getCommittedAmount())).subtract(zero(line.getActualAmount()));
        boolean overrun = line == null || available.compareTo(amount) < 0;
        if (overrun && "REJECT".equals(budget.getControlPolicy())) {
            throw new IllegalArgumentException("费用提交后将超过预算可用额度，当前可用 " + available);
        }
        if (line != null) {
            line.setCommittedAmount(zero(line.getCommittedAmount()).add(amount));
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
            BigDecimal amount = zero(expense.getAmount());
            line.setCommittedAmount(zero(line.getCommittedAmount()).subtract(amount).max(BigDecimal.ZERO));
            line.setActualAmount(zero(line.getActualAmount()).add(amount));
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
            line.setCommittedAmount(zero(line.getCommittedAmount()).subtract(zero(expense.getAmount())).max(BigDecimal.ZERO));
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
            line.setActualAmount(zero(line.getActualAmount()).subtract(zero(expense.getAmount())).max(BigDecimal.ZERO));
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

    private BudgetEntity activeBudget(AuditMetadata audit, int year) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<BudgetEntity>()
                .eq(BudgetEntity::getCompanyId, audit.companyId()).eq(BudgetEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetEntity::getBudgetYear, year).eq(BudgetEntity::getStatus, "APPROVED").eq(BudgetEntity::getDeletedFlag, 0)
                .orderByDesc(BudgetEntity::getId).last("limit 1"));
    }

    private BudgetLineEntity resolvedLine(AuditMetadata audit, BudgetEntity budget, LocalDate date, Long deptId, Long subjectId) {
        if (budget == null) return null;
        BudgetLineEntity line = findLine(audit, budget.getId(), date.getMonthValue(), deptId, subjectId);
        return line == null ? findLine(audit, budget.getId(), 0, deptId, subjectId) : line;
    }

    private BudgetLineEntity findLine(AuditMetadata audit, Long budgetId, int month, Long deptId, Long subjectId) {
        return budgetLineMapper.selectOne(new LambdaQueryWrapper<BudgetLineEntity>()
                .eq(BudgetLineEntity::getCompanyId, audit.companyId()).eq(BudgetLineEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetLineEntity::getBudgetId, budgetId).eq(BudgetLineEntity::getPeriodMonth, month)
                .eq(BudgetLineEntity::getSubjectId, subjectId)
                .eq(deptId != null, BudgetLineEntity::getDeptId, deptId)
                .isNull(deptId == null, BudgetLineEntity::getDeptId));
    }
    private BudgetLineEntity budgetLine(ExpenseEntity expense, AuditMetadata audit) {
        if (expense.getBudgetLineId() == null) return null;
        BudgetLineEntity line = budgetLineMapper.selectById(expense.getBudgetLineId());
        if (line == null || !audit.companyId().equals(line.getCompanyId()) || !audit.accountBookId().equals(line.getAccountBookId())) return null;
        return line;
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : ScalePrecision.amount(value); }
}

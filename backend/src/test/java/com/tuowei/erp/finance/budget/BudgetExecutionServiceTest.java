package com.tuowei.erp.finance.budget;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.service.BudgetExecutionService;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetExecutionServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(9L, 1L, 2L,
            LocalDateTime.of(2026, 8, 25, 10, 0));

    @Mock private BudgetMapper budgetMapper;
    @Mock private BudgetLineMapper budgetLineMapper;
    @Mock private ExpenseMapper expenseMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    private BudgetExecutionService service;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        service = new BudgetExecutionService(budgetMapper, budgetLineMapper, expenseMapper, auditMetadataFactory);
    }

    @Test
    void monthlyLineTakesPriorityAndPreviewIncludesProjectedBalance() {
        when(budgetMapper.selectOne(any())).thenReturn(budget("APPROVAL"));
        when(budgetLineMapper.selectOne(any())).thenReturn(line(8, "100.00", "20.00", "10.00"));

        BudgetExecutionResponse result = service.check(
                LocalDate.of(2026, 8, 1), 31L, 6602L, new BigDecimal("25.00"));

        assertThat(result.periodSource()).isEqualTo("MONTHLY");
        assertThat(result.budgetLineId()).isEqualTo(200L);
        assertThat(result.availableAmount()).isEqualByComparingTo("70.00");
        assertThat(result.projectedAvailableAmount()).isEqualByComparingTo("45.00");
        assertThat(result.overrun()).isFalse();
    }

    @Test
    void annualLineIsUsedWhenMonthlyLineDoesNotExist() {
        when(budgetMapper.selectOne(any())).thenReturn(budget("REJECT"));
        when(budgetLineMapper.selectOne(any())).thenReturn(null, line(0, "500.00", "50.00", "100.00"));

        BudgetExecutionResponse result = service.check(
                LocalDate.of(2026, 8, 1), 31L, 6602L, new BigDecimal("400.00"));

        assertThat(result.periodSource()).isEqualTo("ANNUAL");
        assertThat(result.availableAmount()).isEqualByComparingTo("350.00");
        assertThat(result.projectedAvailableAmount()).isEqualByComparingTo("-50.00");
        assertThat(result.overrun()).isTrue();
    }

    @Test
    void rejectPolicyBlocksOverrunWithoutWritingAnything() {
        when(budgetMapper.selectOne(any())).thenReturn(budget("REJECT"));
        when(budgetLineMapper.selectOne(any())).thenReturn(line(8, "100.00", "80.00", "0.00"));
        ExpenseEntity expense = expense("NONE", "30.00");

        assertThatThrownBy(() -> service.commitExpense(expense))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过预算");

        verify(budgetLineMapper, never()).updateById(any(BudgetLineEntity.class));
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void approvalPolicyAllowsOverrunAndMarksExpense() {
        when(budgetMapper.selectOne(any())).thenReturn(budget("APPROVAL"));
        BudgetLineEntity line = line(8, "100.00", "80.00", "0.00");
        when(budgetLineMapper.selectOne(any())).thenReturn(line);
        when(budgetLineMapper.updateById(any(BudgetLineEntity.class))).thenReturn(1);
        when(expenseMapper.updateById(any(ExpenseEntity.class))).thenReturn(1);
        ExpenseEntity expense = expense("NONE", "30.00");

        service.commitExpense(expense);

        assertThat(line.getCommittedAmount()).isEqualByComparingTo("110.00");
        assertThat(expense.getBudgetState()).isEqualTo("COMMITTED");
        assertThat(expense.getBudgetLineId()).isEqualTo(200L);
        assertThat(expense.getBudgetOverrunFlag()).isEqualTo(1);
    }

    @Test
    void committedExpenseIsIdempotent() {
        when(budgetMapper.selectOne(any())).thenReturn(budget("APPROVAL"));
        ExpenseEntity expense = expense("COMMITTED", "30.00");

        service.commitExpense(expense);

        verify(budgetLineMapper, never()).selectOne(any());
        verify(budgetLineMapper, never()).updateById(any(BudgetLineEntity.class));
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void actualizeReleaseAndReverseMoveAmountsExactlyOnce() {
        BudgetLineEntity actualLine = line(8, "100.00", "30.00", "10.00");
        when(budgetLineMapper.selectById(200L)).thenReturn(actualLine);
        when(budgetLineMapper.updateById(any(BudgetLineEntity.class))).thenReturn(1);
        when(expenseMapper.updateById(any(ExpenseEntity.class))).thenReturn(1);
        ExpenseEntity expense = expense("COMMITTED", "30.00");
        expense.setBudgetLineId(200L);

        service.actualizeExpense(expense);
        assertThat(actualLine.getCommittedAmount()).isEqualByComparingTo("0.00");
        assertThat(actualLine.getActualAmount()).isEqualByComparingTo("40.00");
        assertThat(expense.getBudgetState()).isEqualTo("ACTUAL");

        service.reverseExpense(expense);
        assertThat(actualLine.getActualAmount()).isEqualByComparingTo("10.00");
        assertThat(expense.getBudgetState()).isEqualTo("REVERSED");

        BudgetLineEntity releaseLine = line(8, "100.00", "30.00", "0.00");
        when(budgetLineMapper.selectById(200L)).thenReturn(releaseLine);
        ExpenseEntity rejected = expense("COMMITTED", "30.00");
        rejected.setBudgetLineId(200L);
        service.releaseExpense(rejected);
        assertThat(releaseLine.getCommittedAmount()).isEqualByComparingTo("0.00");
        assertThat(rejected.getBudgetState()).isEqualTo("RELEASED");
    }

    private BudgetEntity budget(String policy) {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(100L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBudgetYear(2026);
        entity.setControlPolicy(policy);
        entity.setStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private BudgetLineEntity line(int month, String budget, String committed, String actual) {
        BudgetLineEntity entity = new BudgetLineEntity();
        entity.setId(200L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBudgetId(100L);
        entity.setPeriodMonth(month);
        entity.setDeptId(31L);
        entity.setSubjectId(6602L);
        entity.setBudgetAmount(new BigDecimal(budget));
        entity.setCommittedAmount(new BigDecimal(committed));
        entity.setActualAmount(new BigDecimal(actual));
        entity.setVersion(0);
        return entity;
    }

    private ExpenseEntity expense(String budgetState, String amount) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(300L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setExpenseDate(LocalDate.of(2026, 8, 25));
        entity.setDeptId(31L);
        entity.setSubjectId(6602L);
        entity.setAmount(new BigDecimal(amount));
        entity.setBudgetState(budgetState);
        entity.setBudgetOverrunFlag(0);
        entity.setVersion(0);
        return entity;
    }
}

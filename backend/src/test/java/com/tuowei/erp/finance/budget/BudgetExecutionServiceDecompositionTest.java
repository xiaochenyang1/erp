package com.tuowei.erp.finance.budget;

import com.tuowei.erp.finance.budget.service.BudgetExecutionCommandService;
import com.tuowei.erp.finance.budget.service.BudgetExecutionQueryService;
import com.tuowei.erp.finance.budget.service.BudgetExecutionService;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetExecutionServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(BudgetExecutionService.class))
                .containsExactlyInAnyOrder(
                        BudgetExecutionQueryService.class,
                        BudgetExecutionCommandService.class
                );
        assertThat(constructorDependencies(BudgetExecutionCommandService.class))
                .contains(BudgetExecutionQueryService.class)
                .doesNotContain(BudgetExecutionService.class);
        assertThat(constructorDependencies(BudgetExecutionQueryService.class))
                .doesNotContain(
                        BudgetExecutionService.class,
                        BudgetExecutionCommandService.class
                );
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        BudgetExecutionQueryService queryService = mock(BudgetExecutionQueryService.class);
        BudgetExecutionCommandService commandService = mock(BudgetExecutionCommandService.class);
        BudgetExecutionService service = new BudgetExecutionService(queryService, commandService);

        BudgetExecutionResponse response = mock(BudgetExecutionResponse.class);
        ExpenseEntity expense = mock(ExpenseEntity.class);
        LocalDate date = LocalDate.of(2026, 8, 1);
        BigDecimal amount = new BigDecimal("25.00");

        when(queryService.check(date, 31L, 6602L, amount)).thenReturn(response);

        assertThat(service.check(date, 31L, 6602L, amount)).isSameAs(response);
        service.commitExpense(expense);
        service.actualizeExpense(expense);
        service.releaseExpense(expense);
        service.reverseExpense(expense);

        verify(queryService).check(date, 31L, 6602L, amount);
        verify(commandService).commitExpense(expense);
        verify(commandService).actualizeExpense(expense);
        verify(commandService).releaseExpense(expense);
        verify(commandService).reverseExpense(expense);
    }

    @Test
    void facadeAndQueryServiceKeepCheckReadOnly() throws NoSuchMethodException {
        assertReadOnly(BudgetExecutionService.class.getDeclaredMethod("check", LocalDate.class, Long.class, Long.class, BigDecimal.class));
        assertReadOnly(BudgetExecutionQueryService.class.getDeclaredMethod("check", LocalDate.class, Long.class, Long.class, BigDecimal.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{BudgetExecutionService.class, BudgetExecutionCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("commitExpense", ExpenseEntity.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("actualizeExpense", ExpenseEntity.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("releaseExpense", ExpenseEntity.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("reverseExpense", ExpenseEntity.class));
        }
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

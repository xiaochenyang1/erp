package com.tuowei.erp.finance.expense;

import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.service.ExpensePostingService;
import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseServiceDecompositionTest {

    @Test
    void facadeKeepsFilteringTenantGuardVoucherHydrationAndMappingBehindQueryService() {
        assertThat(constructorDependencies(ExpenseService.class))
                .contains(ExpenseQueryService.class, ExpensePostingService.class)
                .doesNotContain(VoucherMapper.class, VoucherEntryMapper.class);
        assertThat(constructorDependencies(ExpenseQueryService.class))
                .contains(VoucherMapper.class, VoucherEntryMapper.class)
                .doesNotContain(ExpenseService.class);
        assertThat(constructorDependencies(ExpensePostingService.class))
                .contains(ExpenseQueryService.class, VoucherMapper.class, VoucherEntryMapper.class)
                .doesNotContain(ExpenseService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ExpenseService.class.getDeclaredMethod("list", ExpensePageQuery.class));
        assertReadOnly(ExpenseService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ExpenseService.class.getDeclaredMethod("reconciliation", Long.class));
        assertReadOnly(ExpenseQueryService.class.getDeclaredMethod("list", ExpensePageQuery.class));
        assertReadOnly(ExpenseQueryService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ExpenseQueryService.class.getDeclaredMethod("reconciliation", Long.class));
        assertReadOnly(ExpenseQueryService.class.getDeclaredMethod("requireExpense", Long.class));
    }

    @Test
    void approvalAndPostingFlowKeepRequiredTransactionsOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod(
                "create",
                com.tuowei.erp.finance.expense.web.ExpenseCreateRequest.class
        ));
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod("reverse", Long.class));
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(ExpensePostingService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ExpensePostingService.class.getDeclaredMethod("reverse", Long.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
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
}

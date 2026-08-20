package com.tuowei.erp.finance.expense;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.service.ExpenseCommandService;
import com.tuowei.erp.finance.expense.service.ExpenseNumberService;
import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.service.ExpensePostingService;
import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
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
    void facadeKeepsAllOrchestrationBehindDedicatedCollaborators() {
        assertThat(constructorDependencies(ExpenseService.class))
                .containsExactlyInAnyOrder(
                        ExpenseQueryService.class,
                        ExpenseCommandService.class,
                        ExpensePostingService.class
                );
        assertThat(constructorDependencies(ExpenseQueryService.class))
                .contains(VoucherMapper.class, VoucherEntryMapper.class)
                .doesNotContain(ExpenseService.class, ExpenseCommandService.class);
        assertThat(constructorDependencies(ExpenseCommandService.class))
                .containsExactlyInAnyOrder(
                        ExpenseMapper.class,
                        ExpenseNumberService.class,
                        AccountSubjectService.class,
                        AuditMetadataFactory.class,
                        ExpenseQueryService.class,
                        AccountPeriodGuard.class,
                        AttachmentService.class,
                        WorkflowService.class
                )
                .doesNotContain(ExpenseService.class, ExpensePostingService.class);
        assertThat(constructorDependencies(ExpensePostingService.class))
                .contains(ExpenseQueryService.class, VoucherMapper.class, VoucherEntryMapper.class)
                .doesNotContain(ExpenseService.class, ExpenseCommandService.class, WorkflowService.class);
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
    void commandAndPostingFlowsKeepRequiredTransactions() throws NoSuchMethodException {
        Class<?>[] commandServices = {ExpenseService.class, ExpenseCommandService.class};
        for (Class<?> serviceType : commandServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "create",
                    com.tuowei.erp.finance.expense.web.ExpenseCreateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "update",
                    Long.class,
                    com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("submit", Long.class, String.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("approve", Long.class, String.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "approveWorkflowTask",
                    Long.class,
                    Long.class,
                    String.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("reject", Long.class, String.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "rejectWorkflowTask",
                    Long.class,
                    Long.class,
                    String.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("cancel", Long.class));
        }
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ExpenseService.class.getDeclaredMethod("reverse", Long.class));
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

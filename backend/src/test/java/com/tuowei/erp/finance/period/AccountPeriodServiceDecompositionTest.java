package com.tuowei.erp.finance.period;

import com.tuowei.erp.finance.period.service.AccountPeriodCommandService;
import com.tuowei.erp.finance.period.service.AccountPeriodQueryService;
import com.tuowei.erp.finance.period.service.AccountPeriodService;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseSnapshotResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPeriodServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(AccountPeriodService.class))
                .containsExactlyInAnyOrder(
                        AccountPeriodQueryService.class,
                        AccountPeriodCommandService.class
                );
        assertThat(constructorDependencies(AccountPeriodCommandService.class))
                .contains(AccountPeriodQueryService.class)
                .doesNotContain(AccountPeriodService.class);
        assertThat(constructorDependencies(AccountPeriodQueryService.class))
                .doesNotContain(
                        AccountPeriodService.class,
                        AccountPeriodCommandService.class
                );
    }

    @Test
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        AccountPeriodQueryService queryService = mock(AccountPeriodQueryService.class);
        AccountPeriodCommandService commandService = mock(AccountPeriodCommandService.class);
        AccountPeriodService service = new AccountPeriodService(queryService, commandService);

        List<AccountPeriodResponse> generated = List.of(mock(AccountPeriodResponse.class));
        List<AccountPeriodResponse> listed = List.of(mock(AccountPeriodResponse.class));
        List<AccountPeriodCloseSnapshotResponse> snapshots = List.of(mock(AccountPeriodCloseSnapshotResponse.class));
        AccountPeriodResponse locked = mock(AccountPeriodResponse.class);
        AccountPeriodResponse closed = mock(AccountPeriodResponse.class);
        AccountPeriodResponse reopened = mock(AccountPeriodResponse.class);

        when(commandService.generate(2026)).thenReturn(generated);
        when(queryService.list(2026)).thenReturn(listed);
        when(queryService.closeSnapshots(5L)).thenReturn(snapshots);
        when(commandService.lock(5L)).thenReturn(locked);
        when(commandService.close(5L)).thenReturn(closed);
        when(commandService.reopen(5L)).thenReturn(reopened);

        assertThat(service.generate(2026)).isSameAs(generated);
        assertThat(service.list(2026)).isSameAs(listed);
        assertThat(service.closeSnapshots(5L)).isSameAs(snapshots);
        assertThat(service.lock(5L)).isSameAs(locked);
        assertThat(service.close(5L)).isSameAs(closed);
        assertThat(service.reopen(5L)).isSameAs(reopened);

        verify(commandService).generate(2026);
        verify(queryService).list(2026);
        verify(queryService).closeSnapshots(5L);
        verify(commandService).lock(5L);
        verify(commandService).close(5L);
        verify(commandService).reopen(5L);
    }

    @Test
    void facadeAndQueryServiceKeepReadsReadOnly() throws NoSuchMethodException {
        assertReadOnly(AccountPeriodService.class.getDeclaredMethod("list", Integer.class));
        assertReadOnly(AccountPeriodService.class.getDeclaredMethod("closeSnapshots", Long.class));
        assertReadOnly(AccountPeriodQueryService.class.getDeclaredMethod("list", Integer.class));
        assertReadOnly(AccountPeriodQueryService.class.getDeclaredMethod("closeSnapshots", Long.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{AccountPeriodService.class, AccountPeriodCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("generate", Integer.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("lock", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("close", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("reopen", Long.class));
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

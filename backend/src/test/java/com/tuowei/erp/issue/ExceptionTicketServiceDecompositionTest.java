package com.tuowei.erp.issue;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.issue.service.ExceptionTicketQueryService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTicketServiceDecompositionTest {

    @Test
    void facadeKeepsReadFilteringEventHydrationAndMappingBehindQueryService() {
        assertThat(constructorDependencies(ExceptionTicketService.class))
                .contains(ExceptionTicketQueryService.class);
        assertThat(constructorDependencies(ExceptionTicketQueryService.class))
                .contains(Clock.class)
                .doesNotContain(ExceptionTicketService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ExceptionTicketService.class.getDeclaredMethod("list", ExceptionTicketPageQuery.class));
        assertReadOnly(ExceptionTicketQueryService.class.getDeclaredMethod("list", ExceptionTicketPageQuery.class));
        assertReadOnly(ExceptionTicketQueryService.class.getDeclaredMethod(
                "requireTicket",
                Long.class,
                AuditMetadata.class
        ));
    }

    @Test
    void writeStateMachineKeepsRequiredTransactionsOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ExceptionTicketService.class.getDeclaredMethod(
                "create",
                com.tuowei.erp.issue.web.ExceptionTicketCreateRequest.class
        ));
        assertRequiredWriteTransaction(ExceptionTicketService.class.getDeclaredMethod(
                "assign",
                Long.class,
                com.tuowei.erp.issue.web.ExceptionTicketAssignRequest.class
        ));
        assertRequiredWriteTransaction(ExceptionTicketService.class.getDeclaredMethod(
                "escalateOverdueTickets",
                java.time.LocalDateTime.class
        ));
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

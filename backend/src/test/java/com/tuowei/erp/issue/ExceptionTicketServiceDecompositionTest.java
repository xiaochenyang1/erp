package com.tuowei.erp.issue;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.mapper.ExceptionTicketEventMapper;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.service.ExceptionTicketQueryService;
import com.tuowei.erp.issue.service.ExceptionTicketCommandService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
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
    void facadeKeepsOnlyDedicatedQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(ExceptionTicketService.class))
                .containsExactlyInAnyOrder(
                        ExceptionTicketQueryService.class,
                        ExceptionTicketCommandService.class
                );
        assertThat(constructorDependencies(ExceptionTicketService.class))
                .doesNotContain(
                        AuditMetadataFactory.class,
                        ExceptionTicketMapper.class,
                        ExceptionTicketEventMapper.class,
                        NotificationService.class,
                        ExceptionSlaPolicyService.class,
                        Clock.class
                );
        assertThat(constructorDependencies(ExceptionTicketQueryService.class))
                .containsExactlyInAnyOrder(
                        AuditMetadataFactory.class,
                        ExceptionTicketMapper.class,
                        ExceptionTicketEventMapper.class,
                        Clock.class
                )
                .doesNotContain(ExceptionTicketService.class);
        assertThat(constructorDependencies(ExceptionTicketCommandService.class))
                .containsExactlyInAnyOrder(
                        AuditMetadataFactory.class,
                        ExceptionTicketMapper.class,
                        ExceptionTicketEventMapper.class,
                        NotificationService.class,
                        ExceptionSlaPolicyService.class,
                        ExceptionTicketQueryService.class,
                        Clock.class
                )
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
    void writeStateMachineKeepsRequiredTransactionsOnFacadeAndCommandService() throws NoSuchMethodException {
        Class<?>[] writeMethods = {
                ExceptionTicketService.class,
                ExceptionTicketCommandService.class
        };
        for (Class<?> serviceType : writeMethods) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "create", ExceptionTicketCreateRequest.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "create", ExceptionTicketCreateRequest.class, AuditMetadata.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "assign", Long.class, ExceptionTicketAssignRequest.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "start", Long.class, ExceptionTicketActionRequest.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "resolve", Long.class, ExceptionTicketActionRequest.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "close", Long.class, ExceptionTicketActionRequest.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "escalateOverdueTickets", java.time.LocalDateTime.class));
        }
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

package com.tuowei.erp.issue.sla;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.sla.mapper.ExceptionSlaPolicyMapper;
import com.tuowei.erp.issue.sla.service.ExceptionSlaEscalationPolicy;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyCommandService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyQueryService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExceptionSlaPolicyServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(ExceptionSlaPolicyService.class))
                .containsExactlyInAnyOrder(
                        ExceptionSlaPolicyQueryService.class,
                        ExceptionSlaPolicyCommandService.class
                );
        assertThat(constructorDependencies(ExceptionSlaPolicyQueryService.class))
                .containsExactly(ExceptionSlaPolicyMapper.class)
                .doesNotContain(ExceptionSlaPolicyService.class, ExceptionSlaPolicyCommandService.class,
                        AuditMetadataFactory.class);
        assertThat(constructorDependencies(ExceptionSlaPolicyCommandService.class))
                .containsExactlyInAnyOrder(
                        AuditMetadataFactory.class,
                        ExceptionSlaPolicyMapper.class,
                        ExceptionSlaPolicyQueryService.class
                )
                .doesNotContain(ExceptionSlaPolicyService.class);
    }
    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        ExceptionSlaPolicyQueryService queryService = mock(ExceptionSlaPolicyQueryService.class);
        ExceptionSlaPolicyCommandService commandService = mock(ExceptionSlaPolicyCommandService.class);
        ExceptionSlaPolicyService service = new ExceptionSlaPolicyService(queryService, commandService);

        ExceptionSlaPolicyPageQuery query = new ExceptionSlaPolicyPageQuery();
        ExceptionSlaPolicyUpdateRequest request = new ExceptionSlaPolicyUpdateRequest();
        PageResponse<ExceptionSlaPolicyResponse> pageResponse = mock(PageResponse.class);
        ExceptionSlaPolicyResponse response = mock(ExceptionSlaPolicyResponse.class);
        ExceptionTicketEntity ticket = mock(ExceptionTicketEntity.class);
        ExceptionSlaEscalationPolicy escalation = new ExceptionSlaEscalationPolicy(true, "HIGH");
        AuditMetadata audit = new AuditMetadata(1L, 1L, 884001L, LocalDateTime.of(2026, 6, 2, 9, 0));
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 2, 9, 0);
        LocalDateTime dueTime = LocalDateTime.of(2026, 6, 5, 9, 0);

        when(commandService.list(query)).thenReturn(pageResponse);
        when(commandService.update(7L, request)).thenReturn(response);
        when(queryService.resolveDueTime("LOW_STOCK", "HIGH", createdAt, audit)).thenReturn(dueTime);
        when(queryService.resolveEscalation(ticket, audit)).thenReturn(escalation);

        assertThat(service.list(query)).isSameAs(pageResponse);
        assertThat(service.update(7L, request)).isSameAs(response);
        assertThat(service.resolveDueTime("LOW_STOCK", "HIGH", createdAt, audit)).isEqualTo(dueTime);
        assertThat(service.resolveEscalation(ticket, audit)).isSameAs(escalation);

        verify(commandService).list(query);
        verify(commandService).update(7L, request);
        verify(queryService).resolveDueTime("LOW_STOCK", "HIGH", createdAt, audit);
        verify(queryService).resolveEscalation(ticket, audit);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyResolvers() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ExceptionSlaPolicyService.class, ExceptionSlaPolicyQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("resolveDueTime",
                    String.class, String.class, LocalDateTime.class, AuditMetadata.class));
            assertReadOnly(type.getDeclaredMethod("resolveEscalation",
                    ExceptionTicketEntity.class, AuditMetadata.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ExceptionSlaPolicyService.class, ExceptionSlaPolicyCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("list", ExceptionSlaPolicyPageQuery.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("update", Long.class, ExceptionSlaPolicyUpdateRequest.class));
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

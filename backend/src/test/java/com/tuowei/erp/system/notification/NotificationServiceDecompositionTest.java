package com.tuowei.erp.system.notification;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.mapper.NotificationRecipientMapper;
import com.tuowei.erp.system.notification.service.NotificationCommandService;
import com.tuowei.erp.system.notification.service.NotificationQueryService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.notification.service.NotificationWebhookPublisher;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import com.tuowei.erp.system.notification.web.NotificationResponse;
import com.tuowei.erp.workflow.model.WorkflowInstanceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(NotificationService.class))
                .containsExactlyInAnyOrder(NotificationQueryService.class, NotificationCommandService.class);
        assertThat(constructorDependencies(NotificationQueryService.class))
                .containsExactlyInAnyOrder(
                        NotificationMapper.class,
                        NotificationRecipientMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(NotificationService.class, NotificationCommandService.class,
                        NotificationWebhookPublisher.class);
        assertThat(constructorDependencies(NotificationCommandService.class))
                .containsExactlyInAnyOrder(
                        NotificationMapper.class,
                        NotificationRecipientMapper.class,
                        AuditMetadataFactory.class,
                        NotificationWebhookPublisher.class,
                        NotificationQueryService.class
                )
                .doesNotContain(NotificationService.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        NotificationQueryService queryService = mock(NotificationQueryService.class);
        NotificationCommandService commandService = mock(NotificationCommandService.class);
        NotificationService service = new NotificationService(queryService, commandService);

        NotificationPageQuery query = new NotificationPageQuery();
        PageResponse<NotificationResponse> pageResponse = mock(PageResponse.class);
        NotificationResponse response = mock(NotificationResponse.class);
        WorkflowInstanceEntity instance = mock(WorkflowInstanceEntity.class);
        AuditMetadata audit = new AuditMetadata(1L, 1L, 884001L, LocalDateTime.of(2026, 6, 2, 9, 0));
        LocalDateTime now = LocalDateTime.of(2026, 6, 2, 9, 0);
        List<Long> ids = List.of(7L, 8L);

        when(queryService.listMine(query)).thenReturn(pageResponse);
        when(queryService.countUnreadMine()).thenReturn(3L);
        when(commandService.markRead(5L)).thenReturn(response);
        when(commandService.markBatchRead(ids)).thenReturn(2);
        when(commandService.createBusinessNotificationIfAbsent(
                "NOTICE", "T", "title", "content", "PO", 9L, "NO-1", "/url", ids, "dedup", audit, now))
                .thenReturn(true);

        assertThat(service.listMine(query)).isSameAs(pageResponse);
        assertThat(service.countUnreadMine()).isEqualTo(3L);
        assertThat(service.markRead(5L)).isSameAs(response);
        service.markAllRead();
        assertThat(service.markBatchRead(ids)).isEqualTo(2);
        service.createWorkflowPending(instance, ids, audit, now);
        service.closeWorkflowPending(instance, audit, now);
        service.closeWorkflowPendingForUser(instance, 6L, audit, now);
        service.notifyWorkflowResult(instance, "APPROVE", "ok", audit, now);
        service.createBusinessNotification(
                "NOTICE", "T", "title", "content", "PO", 9L, "NO-1", "/url", ids, audit, now);
        assertThat(service.createBusinessNotificationIfAbsent(
                "NOTICE", "T", "title", "content", "PO", 9L, "NO-1", "/url", ids, "dedup", audit, now)).isTrue();

        verify(queryService).listMine(query);
        verify(queryService).countUnreadMine();
        verify(commandService).markRead(5L);
        verify(commandService).markAllRead();
        verify(commandService).markBatchRead(ids);
        verify(commandService).createWorkflowPending(instance, ids, audit, now);
        verify(commandService).closeWorkflowPending(instance, audit, now);
        verify(commandService).closeWorkflowPendingForUser(instance, 6L, audit, now);
        verify(commandService).notifyWorkflowResult(instance, "APPROVE", "ok", audit, now);
        verify(commandService).createBusinessNotification(
                "NOTICE", "T", "title", "content", "PO", 9L, "NO-1", "/url", ids, audit, now);
        verify(commandService).createBusinessNotificationIfAbsent(
                "NOTICE", "T", "title", "content", "PO", 9L, "NO-1", "/url", ids, "dedup", audit, now);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{NotificationService.class, NotificationQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("listMine", NotificationPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("countUnreadMine"));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{NotificationService.class, NotificationCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("markRead", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("markAllRead"));
            assertRequiredWriteTransaction(type.getDeclaredMethod("markBatchRead", List.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("createWorkflowPending",
                    WorkflowInstanceEntity.class, List.class, AuditMetadata.class, LocalDateTime.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("closeWorkflowPending",
                    WorkflowInstanceEntity.class, AuditMetadata.class, LocalDateTime.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("closeWorkflowPendingForUser",
                    WorkflowInstanceEntity.class, Long.class, AuditMetadata.class, LocalDateTime.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("notifyWorkflowResult",
                    WorkflowInstanceEntity.class, String.class, String.class, AuditMetadata.class, LocalDateTime.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("createBusinessNotification",
                    String.class, String.class, String.class, String.class, String.class, Long.class,
                    String.class, String.class, List.class, AuditMetadata.class, LocalDateTime.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepNoRollbackForDuplicateKeyOnCreateIfAbsent() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{NotificationService.class, NotificationCommandService.class}) {
            Method method = type.getDeclaredMethod("createBusinessNotificationIfAbsent",
                    String.class, String.class, String.class, String.class, String.class, Long.class,
                    String.class, String.class, List.class, String.class, AuditMetadata.class, LocalDateTime.class);
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).isNotNull();
            assertThat(transactional.readOnly()).isFalse();
            assertThat(transactional.noRollbackFor()).contains(DuplicateKeyException.class);
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

package com.tuowei.erp.system.notification;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.mapper.NotificationRecipientMapper;
import com.tuowei.erp.system.notification.service.NotificationQueryService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.notification.service.NotificationWebhookPublisher;
import com.tuowei.erp.system.notification.web.NotificationPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceDecompositionTest {

    @Test
    void facadeKeepsWritesAndDelegatesReadSideToQueryService() {
        assertThat(constructorDependencies(NotificationService.class))
                .containsExactlyInAnyOrder(
                        NotificationMapper.class,
                        NotificationRecipientMapper.class,
                        AuditMetadataFactory.class,
                        NotificationWebhookPublisher.class,
                        NotificationQueryService.class
                );
        assertThat(constructorDependencies(NotificationQueryService.class))
                .containsExactlyInAnyOrder(
                        NotificationMapper.class,
                        NotificationRecipientMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(NotificationService.class, NotificationWebhookPublisher.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        assertReadOnly(NotificationService.class.getDeclaredMethod(
                "listMine", NotificationPageQuery.class));
        assertReadOnly(NotificationService.class.getDeclaredMethod("countUnreadMine"));
        assertReadOnly(NotificationQueryService.class.getDeclaredMethod(
                "listMine", NotificationPageQuery.class));
        assertReadOnly(NotificationQueryService.class.getDeclaredMethod("countUnreadMine"));
    }

    @Test
    void facadeKeepsRequiredTransactionsOnReadStateWrites() throws NoSuchMethodException {
        assertRequiredWriteTransaction(NotificationService.class.getDeclaredMethod("markRead", Long.class));
        assertRequiredWriteTransaction(NotificationService.class.getDeclaredMethod("markAllRead"));
        assertRequiredWriteTransaction(NotificationService.class.getDeclaredMethod("markBatchRead", List.class));
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

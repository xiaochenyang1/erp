package com.tuowei.erp.system.attachment;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.service.AttachmentQueryService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentServiceDecompositionTest {

    @Test
    void facadeKeepsWritesAndDelegatesReadSideToQueryService() {
        assertThat(constructorDependencies(AttachmentService.class))
                .containsExactlyInAnyOrder(
                        AttachmentMapper.class,
                        AuditMetadataFactory.class,
                        AttachmentProperties.class,
                        BusinessTimelineService.class,
                        Clock.class,
                        AttachmentQueryService.class
                );
        assertThat(constructorDependencies(AttachmentQueryService.class))
                .containsExactlyInAnyOrder(
                        AttachmentMapper.class,
                        AuditMetadataFactory.class,
                        AttachmentProperties.class
                )
                .doesNotContain(AttachmentService.class, BusinessTimelineService.class, Clock.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        Class<?>[] readServices = {AttachmentService.class, AttachmentQueryService.class};
        for (Class<?> serviceClass : readServices) {
            assertReadOnly(serviceClass.getDeclaredMethod("list", AttachmentPageQuery.class));
            assertReadOnly(serviceClass.getDeclaredMethod("download", Long.class));
            assertReadOnly(serviceClass.getDeclaredMethod(
                    "downloadForBusiness", Long.class, String.class, Long.class));
            assertReadOnly(serviceClass.getDeclaredMethod(
                    "requireForBusiness", Long.class, String.class, Long.class));
            assertReadOnly(serviceClass.getDeclaredMethod("countActive", String.class, Long.class));
            assertReadOnly(serviceClass.getDeclaredMethod("requireIfConfigured", String.class, Long.class));
        }
    }

    @Test
    void facadeKeepsRequiredTransactionsOnWrites() throws NoSuchMethodException {
        assertRequiredWrite(AttachmentService.class.getDeclaredMethod(
                "upload", String.class, Long.class, String.class, MultipartFile.class));
        assertRequiredWrite(AttachmentService.class.getDeclaredMethod("delete", Long.class));
        assertRequiredWrite(AttachmentService.class.getDeclaredMethod(
                "deleteForBusiness", Long.class, String.class, Long.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

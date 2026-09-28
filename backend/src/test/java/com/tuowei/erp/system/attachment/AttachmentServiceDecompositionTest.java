package com.tuowei.erp.system.attachment;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.service.AttachmentCommandService;
import com.tuowei.erp.system.attachment.service.AttachmentQueryService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.service.AttachmentStorageService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(AttachmentService.class))
                .containsExactlyInAnyOrder(AttachmentQueryService.class, AttachmentCommandService.class);
        assertThat(constructorDependencies(AttachmentQueryService.class))
                .containsExactlyInAnyOrder(
                        AttachmentMapper.class,
                        AuditMetadataFactory.class,
                        AttachmentProperties.class,
                        AttachmentStorageService.class
                )
                .doesNotContain(AttachmentService.class, AttachmentCommandService.class);
        assertThat(constructorDependencies(AttachmentCommandService.class))
                .containsExactlyInAnyOrder(
                        AttachmentMapper.class,
                        AuditMetadataFactory.class,
                        AttachmentProperties.class,
                        BusinessTimelineService.class,
                        AttachmentStorageService.class,
                        AttachmentQueryService.class,
                        Clock.class
                )
                .doesNotContain(AttachmentService.class);
        assertThat(constructorDependencies(AttachmentStorageService.class))
                .containsExactlyInAnyOrder(AttachmentProperties.class, Clock.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        AttachmentQueryService queryService = mock(AttachmentQueryService.class);
        AttachmentCommandService commandService = mock(AttachmentCommandService.class);
        AttachmentService service = new AttachmentService(queryService, commandService);

        MultipartFile file = mock(MultipartFile.class);
        AttachmentResponse response = mock(AttachmentResponse.class);
        PageResponse<AttachmentResponse> pageResponse = mock(PageResponse.class);
        ResponseEntity<Resource> downloadResponse = ResponseEntity.ok(mock(Resource.class));
        AttachmentPageQuery query = new AttachmentPageQuery();

        when(commandService.upload("PO", 1L, "NO-1", file)).thenReturn(response);
        when(queryService.list(query)).thenReturn(pageResponse);
        when(queryService.download(2L)).thenReturn(downloadResponse);
        when(queryService.downloadForBusiness(3L, "PO", 4L)).thenReturn(downloadResponse);
        when(queryService.countActive("PO", 5L)).thenReturn(6L);

        assertThat(service.upload("PO", 1L, "NO-1", file)).isSameAs(response);
        assertThat(service.list(query)).isSameAs(pageResponse);
        assertThat(service.download(2L)).isSameAs(downloadResponse);
        assertThat(service.downloadForBusiness(3L, "PO", 4L)).isSameAs(downloadResponse);
        service.requireForBusiness(7L, "PO", 8L);
        service.delete(9L);
        service.deleteForBusiness(10L, "PO", 11L);
        assertThat(service.countActive("PO", 5L)).isEqualTo(6L);
        service.requireIfConfigured("PO", 12L);

        verify(commandService).upload("PO", 1L, "NO-1", file);
        verify(queryService).list(query);
        verify(queryService).download(2L);
        verify(queryService).downloadForBusiness(3L, "PO", 4L);
        verify(queryService).requireForBusiness(7L, "PO", 8L);
        verify(commandService).delete(9L);
        verify(commandService).deleteForBusiness(10L, "PO", 11L);
        verify(queryService).countActive("PO", 5L);
        verify(queryService).requireIfConfigured("PO", 12L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionBoundaries() throws NoSuchMethodException {
        Class<?>[] readServices = {AttachmentService.class, AttachmentQueryService.class};
        for (Class<?> serviceType : readServices) {
            assertReadOnly(serviceType.getDeclaredMethod("list", AttachmentPageQuery.class));
            assertReadOnly(serviceType.getDeclaredMethod("download", Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("downloadForBusiness", Long.class, String.class, Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("requireForBusiness", Long.class, String.class, Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("countActive", String.class, Long.class));
            assertReadOnly(serviceType.getDeclaredMethod("requireIfConfigured", String.class, Long.class));
        }
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactionBoundaries() throws NoSuchMethodException {
        Class<?>[] writeServices = {AttachmentService.class, AttachmentCommandService.class};
        for (Class<?> serviceType : writeServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "upload", String.class, Long.class, String.class, MultipartFile.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("delete", Long.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "deleteForBusiness", Long.class, String.class, Long.class));
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

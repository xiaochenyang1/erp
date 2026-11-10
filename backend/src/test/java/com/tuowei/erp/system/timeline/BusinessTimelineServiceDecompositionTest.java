package com.tuowei.erp.system.timeline;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.timeline.service.BusinessTimelineCommandService;
import com.tuowei.erp.system.timeline.service.BusinessTimelineQueryService;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import com.tuowei.erp.system.timeline.web.BusinessTimelineCommentRequest;
import com.tuowei.erp.system.timeline.web.BusinessTimelineQuery;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessTimelineServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(BusinessTimelineService.class))
                .containsExactlyInAnyOrder(
                        BusinessTimelineQueryService.class,
                        BusinessTimelineCommandService.class
                );
        assertThat(constructorDependencies(BusinessTimelineCommandService.class))
                .contains(BusinessTimelineQueryService.class)
                .doesNotContain(BusinessTimelineService.class);
        assertThat(constructorDependencies(BusinessTimelineQueryService.class))
                .doesNotContain(
                        BusinessTimelineService.class,
                        BusinessTimelineCommandService.class
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        BusinessTimelineQueryService queryService = mock(BusinessTimelineQueryService.class);
        BusinessTimelineCommandService commandService = mock(BusinessTimelineCommandService.class);
        BusinessTimelineService service = new BusinessTimelineService(queryService, commandService);

        BusinessTimelineQuery query = mock(BusinessTimelineQuery.class);
        BusinessTimelineCommentRequest request = mock(BusinessTimelineCommentRequest.class);
        AttachmentEntity attachment = mock(AttachmentEntity.class);
        AuditMetadata audit = mock(AuditMetadata.class);
        PageResponse<BusinessTimelineResponse> listed = mock(PageResponse.class);
        BusinessTimelineResponse comment = mock(BusinessTimelineResponse.class);

        when(queryService.list(query)).thenReturn(listed);
        when(commandService.createComment(request)).thenReturn(comment);

        assertThat(service.list(query)).isSameAs(listed);
        assertThat(service.createComment(request)).isSameAs(comment);
        service.recordAttachmentUploaded(attachment, audit);
        service.recordAttachmentDeleted(attachment, audit);

        verify(queryService).list(query);
        verify(commandService).createComment(request);
        verify(commandService).recordAttachmentUploaded(attachment, audit);
        verify(commandService).recordAttachmentDeleted(attachment, audit);
    }

    @Test
    void facadeAndQueryServiceKeepListReadOnly() throws NoSuchMethodException {
        assertReadOnly(BusinessTimelineService.class.getDeclaredMethod("list", BusinessTimelineQuery.class));
        assertReadOnly(BusinessTimelineQueryService.class.getDeclaredMethod("list", BusinessTimelineQuery.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{BusinessTimelineService.class, BusinessTimelineCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("createComment", BusinessTimelineCommentRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("recordAttachmentUploaded", AttachmentEntity.class, AuditMetadata.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("recordAttachmentDeleted", AttachmentEntity.class, AuditMetadata.class));
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

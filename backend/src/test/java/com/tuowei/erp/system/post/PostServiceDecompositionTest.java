package com.tuowei.erp.system.post;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.service.PostCommandService;
import com.tuowei.erp.system.post.service.PostQueryService;
import com.tuowei.erp.system.post.service.PostService;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependenciesWithoutCacheCollaborators() {
        assertThat(constructorDependencies(PostService.class))
                .containsExactlyInAnyOrder(PostQueryService.class, PostCommandService.class);
        assertThat(constructorDependencies(PostQueryService.class))
                .containsExactlyInAnyOrder(PostMapper.class, AuditMetadataFactory.class)
                .doesNotContain(PostService.class, PostCommandService.class);
        assertThat(constructorDependencies(PostCommandService.class))
                .containsExactlyInAnyOrder(
                        PostMapper.class, DeptMapper.class, AuditMetadataFactory.class,
                        PostQueryService.class
                )
                .doesNotContain(PostService.class)
                .noneMatch(type -> type.getSimpleName().toLowerCase().contains("cache"));
    }

    @Test
    void facadeDelegatesAllSixApisAndNormalizesNullListQuery() {
        PostQueryService queryService = mock(PostQueryService.class);
        PostCommandService commandService = mock(PostCommandService.class);
        PostService service = new PostService(queryService, commandService);
        PostCreateRequest createRequest = new PostCreateRequest(
                6001L, "BUYER", "采购员", "created"
        );
        PostUpdateRequest updateRequest = new PostUpdateRequest("高级采购员", "updated");

        service.create(createRequest);
        service.list(null);
        service.getById(7L);
        service.update(7L, updateRequest);
        service.enable(7L);
        service.disable(7L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(PostPageQuery.class));
        verify(queryService).getById(7L);
        verify(commandService).update(7L, updateRequest);
        verify(commandService).enable(7L);
        verify(commandService).disable(7L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{PostService.class, PostQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", PostPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{PostService.class, PostCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", PostCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, PostUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

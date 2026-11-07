package com.tuowei.erp.system.dept;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.service.DeptCommandService;
import com.tuowei.erp.system.dept.service.DeptQueryService;
import com.tuowei.erp.system.dept.service.DeptService;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dept.web.DeptUpdateRequest;
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

class DeptServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(DeptService.class))
                .containsExactlyInAnyOrder(DeptQueryService.class, DeptCommandService.class);
        assertThat(constructorDependencies(DeptQueryService.class))
                .containsExactlyInAnyOrder(DeptMapper.class, AuditMetadataFactory.class)
                .doesNotContain(DeptService.class, DeptCommandService.class);
        assertThat(constructorDependencies(DeptCommandService.class))
                .containsExactlyInAnyOrder(
                        DeptMapper.class, AuditMetadataFactory.class, DeptQueryService.class
                )
                .doesNotContain(DeptService.class);
    }

    @Test
    void facadeDelegatesAllSevenApisAndNormalizesNullListQuery() {
        DeptQueryService queryService = mock(DeptQueryService.class);
        DeptCommandService commandService = mock(DeptCommandService.class);
        DeptService service = new DeptService(queryService, commandService);
        DeptCreateRequest createRequest = new DeptCreateRequest(
                1L, "FINANCE", "财务部", 9001L, 10, "created"
        );
        DeptUpdateRequest updateRequest = new DeptUpdateRequest(
                "财务中心", 9002L, 20, "updated"
        );

        service.create(createRequest);
        service.list(null);
        service.tree();
        service.getById(7L);
        service.update(7L, updateRequest);
        service.enable(7L);
        service.disable(7L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(DeptPageQuery.class));
        verify(queryService).tree();
        verify(queryService).getById(7L);
        verify(commandService).update(7L, updateRequest);
        verify(commandService).enable(7L);
        verify(commandService).disable(7L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{DeptService.class, DeptQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", DeptPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("tree"));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{DeptService.class, DeptCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", DeptCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, DeptUpdateRequest.class));
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

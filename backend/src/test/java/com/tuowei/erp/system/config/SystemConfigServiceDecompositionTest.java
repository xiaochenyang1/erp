package com.tuowei.erp.system.config;

import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.service.SystemConfigCommandService;
import com.tuowei.erp.system.config.service.SystemConfigQueryService;
import com.tuowei.erp.system.config.service.SystemConfigService;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import com.tuowei.erp.common.security.AuditMetadataFactory;
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

class SystemConfigServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependenciesAndNoCacheCollaborators() {
        assertThat(constructorDependencies(SystemConfigService.class))
                .containsExactlyInAnyOrder(SystemConfigQueryService.class, SystemConfigCommandService.class);
        assertThat(constructorDependencies(SystemConfigQueryService.class))
                .containsExactlyInAnyOrder(SystemConfigMapper.class)
                .doesNotContain(SystemConfigService.class, SystemConfigCommandService.class);
        assertThat(constructorDependencies(SystemConfigCommandService.class))
                .containsExactlyInAnyOrder(
                        SystemConfigMapper.class,
                        AuditMetadataFactory.class,
                        SystemConfigQueryService.class
                )
                .doesNotContain(SystemConfigService.class)
                .noneMatch(type -> type.getSimpleName().toLowerCase().contains("cache"));
    }

    @Test
    void facadeDelegatesAllSixApisAndNormalizesNullListQuery() {
        SystemConfigQueryService queryService = mock(SystemConfigQueryService.class);
        SystemConfigCommandService commandService = mock(SystemConfigCommandService.class);
        SystemConfigService service = new SystemConfigService(queryService, commandService);
        SystemConfigCreateRequest createRequest = new SystemConfigCreateRequest(
                "ERP_IMPORT_MAX_ROWS", "导入最大行数", "5000", "created"
        );
        SystemConfigUpdateRequest updateRequest = new SystemConfigUpdateRequest(
                "导入上限", "8000", "updated"
        );

        service.create(createRequest);
        service.list(null);
        service.getById(8001L);
        service.update(8001L, updateRequest);
        service.enable(8001L);
        service.disable(8001L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(SystemConfigPageQuery.class));
        verify(queryService).getById(8001L);
        verify(commandService).update(8001L, updateRequest);
        verify(commandService).enable(8001L);
        verify(commandService).disable(8001L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SystemConfigService.class, SystemConfigQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", SystemConfigPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SystemConfigService.class, SystemConfigCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", SystemConfigCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, SystemConfigUpdateRequest.class));
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

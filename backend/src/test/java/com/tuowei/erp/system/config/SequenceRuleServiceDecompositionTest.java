package com.tuowei.erp.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SequenceCounterMapper;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.service.SequenceRuleCommandService;
import com.tuowei.erp.system.config.service.SequenceRuleQueryService;
import com.tuowei.erp.system.config.service.SequenceRuleService;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import com.tuowei.erp.system.log.service.SystemLogService;
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

class SequenceRuleServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependenciesWithoutCacheCollaborators() {
        assertThat(constructorDependencies(SequenceRuleService.class))
                .containsExactlyInAnyOrder(SequenceRuleQueryService.class, SequenceRuleCommandService.class);
        assertThat(constructorDependencies(SequenceRuleQueryService.class))
                .containsExactlyInAnyOrder(SequenceRuleMapper.class, AuditMetadataFactory.class)
                .doesNotContain(SequenceRuleService.class, SequenceRuleCommandService.class);
        assertThat(constructorDependencies(SequenceRuleCommandService.class))
                .containsExactlyInAnyOrder(
                        SequenceRuleMapper.class,
                        SequenceCounterMapper.class,
                        AuditMetadataFactory.class,
                        SystemLogService.class,
                        ObjectMapper.class,
                        SequenceRuleQueryService.class
                )
                .doesNotContain(SequenceRuleService.class)
                .noneMatch(type -> type.getSimpleName().toLowerCase().contains("cache"));
    }

    @Test
    void facadeDelegatesAllSixApisAndNormalizesNullListQuery() {
        SequenceRuleQueryService queryService = mock(SequenceRuleQueryService.class);
        SequenceRuleCommandService commandService = mock(SequenceRuleCommandService.class);
        SequenceRuleService service = new SequenceRuleService(queryService, commandService);
        SequenceRuleCreateRequest createRequest = new SequenceRuleCreateRequest(
                "SALES_ORDER", "SO-", "yyyyMMdd", 5, 12L
        );
        SequenceRuleUpdateRequest updateRequest = new SequenceRuleUpdateRequest(
                "SO2-", "yyyyMMdd", 6, 13L
        );

        service.create(createRequest);
        service.list(null);
        service.getById(7001L);
        service.update(7001L, updateRequest);
        service.enable(7001L);
        service.disable(7001L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(SequenceRulePageQuery.class));
        verify(queryService).getById(7001L);
        verify(commandService).update(7001L, updateRequest);
        verify(commandService).enable(7001L);
        verify(commandService).disable(7001L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SequenceRuleService.class, SequenceRuleQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", SequenceRulePageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SequenceRuleService.class, SequenceRuleCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", SequenceRuleCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, SequenceRuleUpdateRequest.class));
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

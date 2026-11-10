package com.tuowei.erp.system.readiness;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.service.ReadinessCommandService;
import com.tuowei.erp.system.readiness.service.ReadinessQueryService;
import com.tuowei.erp.system.readiness.service.ReadinessService;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(ReadinessService.class))
                .containsExactlyInAnyOrder(ReadinessQueryService.class, ReadinessCommandService.class);
        assertThat(constructorDependencies(ReadinessQueryService.class))
                .containsExactlyInAnyOrder(
                        ReadinessRunMapper.class,
                        ReadinessItemMapper.class,
                        ReadinessEvidenceMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(ReadinessService.class, ReadinessCommandService.class);
        assertThat(constructorDependencies(ReadinessCommandService.class))
                .containsExactlyInAnyOrder(
                        ReadinessRunMapper.class,
                        ReadinessItemMapper.class,
                        ReadinessEvidenceMapper.class,
                        AuditMetadataFactory.class,
                        ReadinessQueryService.class
                )
                .doesNotContain(ReadinessService.class);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionBoundaries() throws NoSuchMethodException {
        assertReadOnly(ReadinessService.class.getDeclaredMethod("listRuns", ReadinessRunPageQuery.class));
        assertReadOnly(ReadinessService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ReadinessQueryService.class.getDeclaredMethod("listRuns", ReadinessRunPageQuery.class));
        assertReadOnly(ReadinessQueryService.class.getDeclaredMethod("detail", Long.class));
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactionBoundaries() throws NoSuchMethodException {
        Class<?>[] writeServices = {ReadinessService.class, ReadinessCommandService.class};
        for (Class<?> serviceType : writeServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "createRun",
                    ReadinessRunCreateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "addItem",
                    Long.class,
                    ReadinessItemCreateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "addEvidence",
                    Long.class,
                    ReadinessEvidenceCreateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "markItemResult",
                    Long.class,
                    ReadinessItemResultRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "decide",
                    Long.class,
                    ReadinessDecisionRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "recordPreflightEvidence",
                    Long.class,
                    ReadinessPreflightResponse.class
            ));
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

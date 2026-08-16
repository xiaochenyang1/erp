package com.tuowei.erp.system.readiness;

import com.tuowei.erp.system.readiness.service.ReadinessQueryService;
import com.tuowei.erp.system.readiness.service.ReadinessService;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessServiceDecompositionTest {

    @Test
    void writeFacadeDependsOnQueryCollaboratorWithoutReverseDependency() {
        assertThat(constructorDependencies(ReadinessService.class))
                .contains(ReadinessQueryService.class);
        assertThat(constructorDependencies(ReadinessQueryService.class))
                .doesNotContain(ReadinessService.class);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactionBoundaries() throws NoSuchMethodException {
        assertReadOnly(ReadinessService.class.getDeclaredMethod("listRuns", ReadinessRunPageQuery.class));
        assertReadOnly(ReadinessService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ReadinessQueryService.class.getDeclaredMethod("listRuns", ReadinessRunPageQuery.class));
        assertReadOnly(ReadinessQueryService.class.getDeclaredMethod("detail", Long.class));
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
}

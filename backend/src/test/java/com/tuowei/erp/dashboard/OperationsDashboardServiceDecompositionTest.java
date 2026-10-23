package com.tuowei.erp.dashboard;

import com.tuowei.erp.dashboard.service.OperationsDashboardPresentationService;
import com.tuowei.erp.dashboard.service.OperationsDashboardQueryService;
import com.tuowei.erp.dashboard.service.OperationsDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsDashboardServiceDecompositionTest {

    @Test
    void facadeKeepsQueryAndPresentationCollaboratorsOnly() {
        assertThat(constructorDependencies(OperationsDashboardService.class))
                .containsExactlyInAnyOrder(
                        OperationsDashboardQueryService.class,
                        OperationsDashboardPresentationService.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(OperationsDashboardService.class.getDeclaredMethod("getOperationsDashboard"));
        assertReadOnly(OperationsDashboardQueryService.class.getDeclaredMethod("load"));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(java.lang.reflect.Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}

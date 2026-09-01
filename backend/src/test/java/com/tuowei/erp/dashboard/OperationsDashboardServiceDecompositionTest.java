package com.tuowei.erp.dashboard;

import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.dashboard.service.OperationsDashboardDataScopeService;
import com.tuowei.erp.dashboard.service.OperationsDashboardPresentationService;
import com.tuowei.erp.dashboard.service.OperationsDashboardQueryService;
import com.tuowei.erp.dashboard.service.OperationsDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
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

    @Test
    void queryUsesDashboardScopePolicyAndKeepsPreviousConstructor() {
        assertThat(Arrays.stream(OperationsDashboardQueryService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .contains(OperationsDashboardDataScopeService.class)
                .doesNotContain(DataScopeService.class);
        assertThat(Arrays.stream(OperationsDashboardQueryService.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isAnnotationPresent(Autowired.class))
                .map(Constructor::getParameterCount))
                .contains(14);
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

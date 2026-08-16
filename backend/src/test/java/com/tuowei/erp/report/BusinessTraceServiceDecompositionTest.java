package com.tuowei.erp.report;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.report.service.BusinessTraceAssemblyService;
import com.tuowei.erp.report.service.BusinessTraceService;
import com.tuowei.erp.report.web.BusinessTraceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTraceServiceDecompositionTest {

    @Test
    void queryFacadeDependsOnPureAssemblyCollaboratorWithoutReverseInfrastructureDependencies() {
        assertThat(constructorDependencies(BusinessTraceService.class))
                .contains(BusinessTraceAssemblyService.class);

        assertThat(constructorDependencies(BusinessTraceAssemblyService.class))
                .doesNotContain(BusinessTraceService.class, CurrentUserContext.class);
        assertThat(Arrays.stream(BusinessTraceAssemblyService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(type -> type.endsWith("Mapper"))
                .noneMatch(type -> type.equals(CurrentUserContext.class.getName()))
                .noneMatch(type -> type.equals(BusinessTraceService.class.getName()));
    }

    @Test
    void traceKeepsReadOnlyTransactionBoundary() throws NoSuchMethodException {
        Method method = BusinessTraceService.class.getDeclaredMethod("trace", BusinessTraceQuery.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

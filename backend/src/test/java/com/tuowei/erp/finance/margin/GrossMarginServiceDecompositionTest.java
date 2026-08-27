package com.tuowei.erp.finance.margin;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.margin.service.GrossMarginAssemblyService;
import com.tuowei.erp.finance.margin.service.GrossMarginQueryService;
import com.tuowei.erp.finance.margin.service.GrossMarginService;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrossMarginServiceDecompositionTest {

    @Test
    void facadeUsesDedicatedQueryAndAssemblyCollaborators() {
        assertThat(autowiredDependencies(GrossMarginService.class))
                .containsExactlyInAnyOrder(GrossMarginQueryService.class, GrossMarginAssemblyService.class);
        assertThat(constructorDependencies(GrossMarginQueryService.class))
                .containsExactlyInAnyOrder(JdbcTemplate.class, AuditMetadataFactory.class)
                .doesNotContain(GrossMarginService.class, GrossMarginAssemblyService.class);
        assertThat(constructorDependencies(GrossMarginAssemblyService.class))
                .doesNotContain(
                        GrossMarginService.class,
                        GrossMarginQueryService.class,
                        JdbcTemplate.class,
                        AuditMetadataFactory.class
                );
    }

    @Test
    void facadeDelegatesToQueryBeforeAssembly() {
        GrossMarginQueryService query = mock(GrossMarginQueryService.class);
        GrossMarginAssemblyService assembly = mock(GrossMarginAssemblyService.class);
        GrossMarginService facade = new GrossMarginService(query, assembly);
        LocalDate dateFrom = LocalDate.of(2026, 8, 1);
        LocalDate dateTo = LocalDate.of(2026, 8, 31);
        GrossMarginQueryService.GrossMarginData data =
                new GrossMarginQueryService.GrossMarginData(dateFrom, dateTo, List.of());
        GrossMarginSummaryResponse expected = new GrossMarginSummaryResponse(
                dateFrom, dateTo, null, null, null, null, List.of());
        when(query.load(dateFrom, dateTo)).thenReturn(data);
        when(assembly.assemble(data)).thenReturn(expected);

        assertThat(facade.summary(dateFrom, dateTo)).isSameAs(expected);

        var ordered = inOrder(query, assembly);
        ordered.verify(query).load(dateFrom, dateTo);
        ordered.verify(assembly).assemble(data);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactionContract() throws NoSuchMethodException {
        assertReadOnly(GrossMarginService.class.getDeclaredMethod(
                "summary", LocalDate.class, LocalDate.class));
        assertReadOnly(GrossMarginQueryService.class.getDeclaredMethod(
                "load", LocalDate.class, LocalDate.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}

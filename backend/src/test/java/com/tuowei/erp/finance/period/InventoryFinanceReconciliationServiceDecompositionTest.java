package com.tuowei.erp.finance.period;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationAssemblyService;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationQueryService;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryFinanceReconciliationServiceDecompositionTest {

    @Test
    void facadeUsesDedicatedQueryAndPureAssemblyCollaborators() {
        assertThat(autowiredDependencies(InventoryFinanceReconciliationService.class))
                .containsExactlyInAnyOrder(
                        InventoryFinanceReconciliationQueryService.class,
                        InventoryFinanceReconciliationAssemblyService.class
                );
        assertThat(constructorDependencies(InventoryFinanceReconciliationQueryService.class))
                .containsExactlyInAnyOrder(
                        AccountPeriodMapper.class,
                        AuditMetadataFactory.class,
                        JdbcTemplate.class
                )
                .doesNotContain(
                        InventoryFinanceReconciliationService.class,
                        InventoryFinanceReconciliationAssemblyService.class
                );
        assertThat(constructorDependencies(InventoryFinanceReconciliationAssemblyService.class))
                .doesNotContain(
                        InventoryFinanceReconciliationService.class,
                        InventoryFinanceReconciliationQueryService.class,
                        AccountPeriodMapper.class,
                        AuditMetadataFactory.class,
                        JdbcTemplate.class
                );
    }

    @Test
    void facadeDelegatesSummaryAndDifferencesInQueryThenAssemblyOrder() {
        InventoryFinanceReconciliationQueryService query = mock(InventoryFinanceReconciliationQueryService.class);
        InventoryFinanceReconciliationAssemblyService assembly =
                mock(InventoryFinanceReconciliationAssemblyService.class);
        InventoryFinanceReconciliationService facade =
                new InventoryFinanceReconciliationService(query, assembly);
        AccountPeriodEntity period = period();
        var summaryData = new InventoryFinanceReconciliationQueryService.SummaryData(
                period, new BigDecimal("100.00"), new BigDecimal("90.00"));
        var differenceData = new InventoryFinanceReconciliationQueryService.DifferenceData(
                period, List.of(), List.of());
        var expectedSummary = new InventoryFinanceReconciliationResponse(
                period.getId(), period.getPeriodMonth(), null, null, null, false);
        List<InventoryFinanceDifferenceResponse> expectedDifferences = List.of();
        InventoryFinanceDifferenceQuery differenceQuery = new InventoryFinanceDifferenceQuery();
        when(query.loadSummary(period.getId())).thenReturn(summaryData);
        when(assembly.assembleSummary(summaryData)).thenReturn(expectedSummary);
        when(query.loadDifferences(period.getId())).thenReturn(differenceData);
        when(assembly.assembleDifferences(differenceData, differenceQuery)).thenReturn(expectedDifferences);

        assertThat(facade.summary(period.getId())).isSameAs(expectedSummary);
        assertThat(facade.differences(period.getId(), differenceQuery)).isSameAs(expectedDifferences);

        var ordered = inOrder(query, assembly);
        ordered.verify(query).loadSummary(period.getId());
        ordered.verify(assembly).assembleSummary(summaryData);
        ordered.verify(query).loadDifferences(period.getId());
        ordered.verify(assembly).assembleDifferences(differenceData, differenceQuery);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactionContract() throws NoSuchMethodException {
        assertReadOnly(InventoryFinanceReconciliationService.class.getDeclaredMethod("summary", Long.class));
        assertReadOnly(InventoryFinanceReconciliationService.class.getDeclaredMethod(
                "differences", Long.class, InventoryFinanceDifferenceQuery.class));
        assertReadOnly(InventoryFinanceReconciliationService.class.getDeclaredMethod(
                "differenceDetail", Long.class, String.class, String.class));
        assertReadOnly(InventoryFinanceReconciliationQueryService.class.getDeclaredMethod(
                "loadSummary", Long.class));
        assertReadOnly(InventoryFinanceReconciliationQueryService.class.getDeclaredMethod(
                "loadDifferences", Long.class));
        assertReadOnly(InventoryFinanceReconciliationQueryService.class.getDeclaredMethod(
                "loadDifferenceDetail", Long.class, String.class, String.class));
    }

    private AccountPeriodEntity period() {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(865001L);
        period.setCompanyId(1L);
        period.setAccountBookId(1L);
        period.setPeriodYear(2036);
        period.setPeriodMonth("2036-05");
        period.setStartDate(LocalDate.of(2036, 5, 1));
        period.setEndDate(LocalDate.of(2036, 5, 31));
        return period;
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

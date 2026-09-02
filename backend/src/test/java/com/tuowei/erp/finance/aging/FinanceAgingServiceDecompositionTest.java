package com.tuowei.erp.finance.aging;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.aging.service.FinanceAgingAssemblyService;
import com.tuowei.erp.finance.aging.service.FinanceAgingQueryService;
import com.tuowei.erp.finance.aging.service.FinanceAgingService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class FinanceAgingServiceDecompositionTest {

    @Test
    void facadeUsesDedicatedQueryAndAssemblyCollaborators() {
        assertThat(autowiredDependencies(FinanceAgingService.class))
                .containsExactlyInAnyOrder(FinanceAgingQueryService.class, FinanceAgingAssemblyService.class);
        assertThat(constructorDependencies(FinanceAgingQueryService.class))
                .containsExactlyInAnyOrder(
                        ReceivableMapper.class,
                        PayableMapper.class,
                        CustomerMapper.class,
                        SupplierMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(FinanceAgingService.class, FinanceAgingAssemblyService.class);
        assertThat(constructorDependencies(FinanceAgingAssemblyService.class))
                .doesNotContain(FinanceAgingService.class, FinanceAgingQueryService.class);
    }

    @Test
    void facadeDelegatesSummaryToQueryThenAssembly() {
        FinanceAgingQueryService query = mock(FinanceAgingQueryService.class);
        FinanceAgingAssemblyService assembly = mock(FinanceAgingAssemblyService.class);
        FinanceAgingService facade = new FinanceAgingService(query, assembly);
        LocalDate asOf = LocalDate.of(2026, 8, 26);
        FinanceAgingQueryService.AgingData data = new FinanceAgingQueryService.AgingData(
                asOf, java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of());
        when(query.load(asOf)).thenReturn(data);

        facade.summary(asOf);

        verify(query).load(asOf);
        verify(assembly).assemble(data);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactionContract() throws NoSuchMethodException {
        assertReadOnly(FinanceAgingService.class.getDeclaredMethod("summary", LocalDate.class));
        assertReadOnly(FinanceAgingQueryService.class.getDeclaredMethod("load", LocalDate.class));
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

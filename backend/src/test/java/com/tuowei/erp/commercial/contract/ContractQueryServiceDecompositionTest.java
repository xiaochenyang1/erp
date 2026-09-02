package com.tuowei.erp.commercial.contract;

import com.tuowei.erp.commercial.contract.service.ContractQueryService;
import com.tuowei.erp.common.security.ContractDataScopeService;
import com.tuowei.erp.common.security.DataScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContractQueryServiceDecompositionTest {

    @Test
    void queryUsesContractScopePolicyAndKeepsPreviousConstructor() {
        assertThat(autowiredConstructorDependencies())
                .contains(ContractDataScopeService.class)
                .doesNotContain(DataScopeService.class);
        assertThat(Arrays.stream(ContractQueryService.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isAnnotationPresent(Autowired.class))
                .map(Constructor::getParameterCount))
                .contains(12);
    }

    private Set<Class<?>> autowiredConstructorDependencies() {
        return Arrays.stream(ContractQueryService.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

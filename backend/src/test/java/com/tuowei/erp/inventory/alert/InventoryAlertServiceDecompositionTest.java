package com.tuowei.erp.inventory.alert;

import com.tuowei.erp.inventory.alert.service.InventoryAlertQueryService;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryAlertServiceDecompositionTest {

    @Test
    void facadeKeepsFilteringBalanceHydrationAndDispositionOverlayBehindQueryService() {
        assertThat(constructorDependencies(InventoryAlertService.class))
                .contains(InventoryAlertQueryService.class)
                .doesNotContain(InventoryBalanceMapper.class);
        assertThat(constructorDependencies(InventoryAlertQueryService.class))
                .contains(InventoryBalanceMapper.class)
                .doesNotContain(InventoryAlertService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(InventoryAlertService.class.getDeclaredMethod(
                "listRules",
                Long.class,
                Long.class,
                Boolean.class
        ));
        assertReadOnly(InventoryAlertService.class.getDeclaredMethod("listLowStock", Long.class, Long.class));
        assertReadOnly(InventoryAlertQueryService.class.getDeclaredMethod(
                "listRules",
                Long.class,
                Long.class,
                Boolean.class
        ));
        assertReadOnly(InventoryAlertQueryService.class.getDeclaredMethod("listLowStock", Long.class, Long.class));
    }

    @Test
    void ruleAndDispositionWritesRemainRequiredTransactionsOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(InventoryAlertService.class.getDeclaredMethod(
                "createRule",
                com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest.class
        ));
        assertRequiredWriteTransaction(InventoryAlertService.class.getDeclaredMethod(
                "handle",
                Long.class,
                Long.class,
                String.class,
                String.class
        ));
        assertRequiredWriteTransaction(InventoryAlertService.class.getDeclaredMethod(
                "reactivate",
                Long.class,
                Long.class
        ));
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

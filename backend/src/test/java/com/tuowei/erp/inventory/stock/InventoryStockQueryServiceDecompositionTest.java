package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.service.InventoryLotQueryService;
import com.tuowei.erp.inventory.stock.service.InventoryStockQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryStockQueryServiceDecompositionTest {

    @Test
    void stockFacadeDelegatesLotQueriesWithoutRetainingLotMapperOrClock() {
        assertThat(constructorDependencies(InventoryStockQueryService.class))
                .contains(InventoryLotQueryService.class)
                .doesNotContain(InventoryLotBalanceMapper.class, Clock.class);
        assertThat(constructorDependencies(InventoryLotQueryService.class))
                .contains(InventoryLotBalanceMapper.class, InventoryTransactionMapper.class, Clock.class)
                .doesNotContain(InventoryStockQueryService.class);
    }

    @Test
    void facadeAndLotCollaboratorKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(InventoryStockQueryService.class.getDeclaredMethod(
                "listLotBalances",
                InventoryLotBalancePageQuery.class
        ));
        assertReadOnly(InventoryStockQueryService.class.getDeclaredMethod("getLotBalanceById", Long.class));
        assertReadOnly(InventoryStockQueryService.class.getDeclaredMethod("traceLot", InventoryLotTraceQuery.class));
        assertReadOnly(InventoryStockQueryService.class.getDeclaredMethod(
                "listLotExpiryAlerts",
                InventoryLotExpiryAlertQuery.class
        ));
        assertReadOnly(InventoryLotQueryService.class.getDeclaredMethod(
                "listLotBalances",
                InventoryLotBalancePageQuery.class
        ));
        assertReadOnly(InventoryLotQueryService.class.getDeclaredMethod("getLotBalanceById", Long.class));
        assertReadOnly(InventoryLotQueryService.class.getDeclaredMethod("traceLot", InventoryLotTraceQuery.class));
        assertReadOnly(InventoryLotQueryService.class.getDeclaredMethod(
                "listLotExpiryAlerts",
                InventoryLotExpiryAlertQuery.class
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
}

package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.service.InventoryReservationAssemblyService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationQueryServiceDecompositionTest {

    @Test
    void springQueryServiceDependsOnPureAssemblyCollaborator() {
        assertThat(autowiredConstructorDependencies(InventoryReservationQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryReservationMapper.class,
                        InventoryReservationEventMapper.class,
                        InventoryBalanceMapper.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        InventoryReservationAssemblyService.class
                );
        assertThat(Arrays.stream(InventoryReservationAssemblyService.class.getDeclaredFields())
                .anyMatch(field -> BaseMapper.class.isAssignableFrom(field.getType())))
                .isFalse();
        assertThat(InventoryReservationAssemblyService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
    }

    @Test
    void previousFiveParameterConstructorRemainsAvailable() throws NoSuchMethodException {
        assertThat(InventoryReservationQueryService.class.getDeclaredConstructor(
                InventoryReservationMapper.class,
                InventoryReservationEventMapper.class,
                InventoryBalanceMapper.class,
                CurrentUserContext.class,
                DataScopeService.class
        )).isNotNull();
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

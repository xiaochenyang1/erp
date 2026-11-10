package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommandService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationPostingServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnReservationCommandAndReleaseServices() {
        assertThat(autowiredDependencies(InventoryReservationPostingService.class))
                .containsExactlyInAnyOrder(
                        InventoryReservationCommandService.class,
                        InventoryReservationReleaseService.class
                );
        assertThat(constructorDependencies(InventoryReservationCommandService.class))
                .containsExactlyInAnyOrder(
                        InventoryBalanceMapper.class,
                        InventoryReservationMapper.class,
                        InventoryReservationEventMapper.class
                )
                .doesNotContain(InventoryReservationPostingService.class, InventoryReservationReleaseService.class);
        assertThat(constructorDependencies(InventoryReservationReleaseService.class))
                .containsExactlyInAnyOrder(
                        InventoryBalanceMapper.class,
                        InventoryReservationMapper.class,
                        InventoryReservationEventMapper.class
                )
                .doesNotContain(InventoryReservationPostingService.class, InventoryReservationCommandService.class);
    }

    @Test
    void facadeAndCollaboratorsKeepRequiredWriteTransactions() {
        assertThat(Arrays.stream(InventoryReservationPostingService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(this::assertRequiredWrite);
        assertThat(Arrays.stream(InventoryReservationCommandService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(this::assertRequiredWrite);
        assertThat(Arrays.stream(InventoryReservationReleaseService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())))
                .allSatisfy(this::assertRequiredWrite);
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

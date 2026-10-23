package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.service.InventoryBalancePostingService;
import com.tuowei.erp.inventory.stock.service.InventoryLocationResolver;
import com.tuowei.erp.inventory.stock.service.InventoryLotPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingQueryService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryTransactionWriter;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPostingServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnPostingQueryAndReservationCollaborators() {
        assertThat(autowiredDependencies(InventoryPostingService.class))
                .containsExactlyInAnyOrder(
                        InventoryBalancePostingService.class,
                        InventoryPostingQueryService.class,
                        InventoryReservationPostingService.class
                );
        assertThat(constructorDependencies(InventoryPostingQueryService.class))
                .containsExactly(InventoryBalanceMapper.class)
                .doesNotContain(InventoryPostingService.class, InventoryBalancePostingService.class);
        assertThat(constructorDependencies(InventoryBalancePostingService.class))
                .containsExactlyInAnyOrder(
                        InventoryBalanceMapper.class,
                        InventoryTransactionWriter.class,
                        ProductMapper.class,
                        InventoryLotPostingService.class,
                        InventoryLocationResolver.class
                )
                .doesNotContain(InventoryPostingService.class, InventoryPostingQueryService.class,
                        InventoryReservationPostingService.class);
        assertThat(constructorDependencies(InventoryReservationPostingService.class))
                .doesNotContain(InventoryPostingService.class, InventoryPostingQueryService.class,
                        InventoryBalancePostingService.class);
    }

    @Test
    void facadeAndDedicatedServicesKeepReadWriteTransactionSemantics() throws NoSuchMethodException {
        assertRequiredWrite(InventoryPostingService.class.getDeclaredMethod(
                "postInbound", InventoryPostingCommand.class, AuditMetadata.class));
        assertRequiredWrite(InventoryBalancePostingService.class.getDeclaredMethod(
                "postInbound", InventoryPostingCommand.class, AuditMetadata.class));
        assertRequiredWrite(InventoryPostingService.class.getDeclaredMethod(
                "postOutbound", InventoryPostingCommand.class, AuditMetadata.class, String.class));
        assertRequiredWrite(InventoryBalancePostingService.class.getDeclaredMethod(
                "postOutbound", InventoryPostingCommand.class, AuditMetadata.class, String.class));
        assertRequiredWrite(InventoryPostingService.class.getDeclaredMethod(
                "postOutboundWithAllocations", InventoryPostingCommand.class, AuditMetadata.class, String.class));
        assertRequiredWrite(InventoryBalancePostingService.class.getDeclaredMethod(
                "postOutboundWithAllocations", InventoryPostingCommand.class, AuditMetadata.class, String.class));

        assertReadOnly(InventoryPostingService.class.getDeclaredMethod(
                "getQtyOnHand", Long.class, Long.class, Long.class, Long.class));
        assertReadOnly(InventoryPostingQueryService.class.getDeclaredMethod(
                "getQtyOnHand", Long.class, Long.class, Long.class, Long.class));
        assertReadOnly(InventoryPostingService.class.getDeclaredMethod(
                "getQtyAvailable", Long.class, Long.class, Long.class, Long.class));
        assertReadOnly(InventoryPostingQueryService.class.getDeclaredMethod(
                "getQtyAvailable", Long.class, Long.class, Long.class, Long.class));

        assertThat(Arrays.stream(InventoryReservationPostingService.class.getDeclaredMethods())
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

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

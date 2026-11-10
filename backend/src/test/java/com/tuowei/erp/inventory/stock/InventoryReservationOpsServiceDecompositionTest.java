package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCheckService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationOpsService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryReservationCheckQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationManualReleaseRequest;
import com.tuowei.erp.inventory.stock.web.InventoryReservationPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSourceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryReservationSummaryQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationOpsServiceDecompositionTest {

    @Test
    void writeFacadeDependsOnQueryAndCheckCollaboratorsWithoutRetainingTheirMappers() {
        assertThat(constructorDependencies(InventoryReservationOpsService.class))
                .contains(InventoryReservationQueryService.class, InventoryReservationCheckService.class)
                .doesNotContain(
                        InventoryReservationMapper.class,
                        InventoryReservationEventMapper.class,
                        InventoryBalanceMapper.class,
                        SalesOrderMapper.class,
                        SalesOrderLineMapper.class
                );
        assertThat(constructorDependencies(InventoryReservationQueryService.class))
                .contains(
                        InventoryReservationMapper.class,
                        InventoryReservationEventMapper.class,
                        InventoryBalanceMapper.class
                )
                .doesNotContain(InventoryReservationOpsService.class, InventoryReservationCheckService.class);
        assertThat(constructorDependencies(InventoryReservationCheckService.class))
                .contains(
                        InventoryReservationMapper.class,
                        InventoryBalanceMapper.class,
                        SalesOrderMapper.class,
                        SalesOrderLineMapper.class
                )
                .doesNotContain(InventoryReservationOpsService.class, InventoryReservationQueryService.class);
    }

    @Test
    void facadeAndCollaboratorsPreserveReadOnlyAndWriteTransactionBoundaries() throws NoSuchMethodException {
        assertReadOnly(InventoryReservationOpsService.class.getDeclaredMethod(
                "listReservations",
                InventoryReservationPageQuery.class
        ));
        assertReadOnly(InventoryReservationOpsService.class.getDeclaredMethod("getReservation", Long.class));
        assertReadOnly(InventoryReservationOpsService.class.getDeclaredMethod(
                "summary",
                InventoryReservationSummaryQuery.class
        ));
        assertReadOnly(InventoryReservationOpsService.class.getDeclaredMethod(
                "source",
                InventoryReservationSourceQuery.class
        ));
        assertReadOnly(InventoryReservationOpsService.class.getDeclaredMethod(
                "checks",
                InventoryReservationCheckQuery.class
        ));
        assertReadOnly(InventoryReservationQueryService.class.getDeclaredMethod(
                "listReservations",
                InventoryReservationPageQuery.class
        ));
        assertReadOnly(InventoryReservationQueryService.class.getDeclaredMethod("getReservation", Long.class));
        assertReadOnly(InventoryReservationQueryService.class.getDeclaredMethod(
                "summary",
                InventoryReservationSummaryQuery.class
        ));
        assertReadOnly(InventoryReservationQueryService.class.getDeclaredMethod(
                "source",
                InventoryReservationSourceQuery.class
        ));
        assertReadOnly(InventoryReservationCheckService.class.getDeclaredMethod(
                "checks",
                InventoryReservationCheckQuery.class
        ));

        Method manualRelease = InventoryReservationOpsService.class.getDeclaredMethod(
                "manualRelease",
                Long.class,
                InventoryReservationManualReleaseRequest.class
        );
        Transactional transactional = manualRelease.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
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

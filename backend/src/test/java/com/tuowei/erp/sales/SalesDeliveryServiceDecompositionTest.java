package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryCommandService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryPostingService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLogisticsUpdateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesDeliveryServiceDecompositionTest {

    @Test
    void salesDeliveryServiceKeepsCommandsQueriesAndPostingBehindDedicatedServices() {
        Set<Class<?>> constructorDependencies = constructorDependencies(SalesDeliveryService.class);

        assertThat(constructorDependencies)
                .containsExactlyInAnyOrder(
                        SalesDeliveryQueryService.class,
                        SalesDeliveryPostingService.class,
                        SalesDeliveryCommandService.class
                );
        assertThat(constructorDependencies(SalesDeliveryCommandService.class))
                .doesNotContain(SalesDeliveryService.class, SalesDeliveryPostingService.class);
        assertThat(constructorDependencies(SalesDeliveryPostingService.class))
                .doesNotContain(SalesDeliveryService.class, SalesDeliveryCommandService.class);
        assertThat(constructorDependencies)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        FinancePostingService.class,
                        AccountPeriodGuard.class,
                        QcInspectionGate.class
                );
    }

    @Test
    void deliveryCommandsKeepRequiredWriteTransactionsOnFacadeAndCollaborators() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SalesDeliveryService.class.getDeclaredMethod(
                "create", SalesDeliveryCreateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryCommandService.class.getDeclaredMethod(
                "create", SalesDeliveryCreateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryService.class.getDeclaredMethod(
                "update", Long.class, SalesDeliveryUpdateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryCommandService.class.getDeclaredMethod(
                "update", Long.class, SalesDeliveryUpdateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryService.class.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(SalesDeliveryCommandService.class.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(SalesDeliveryService.class.getDeclaredMethod(
                "updateLogistics", Long.class, SalesDeliveryLogisticsUpdateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryCommandService.class.getDeclaredMethod(
                "updateLogistics", Long.class, SalesDeliveryLogisticsUpdateRequest.class));
        assertRequiredWriteTransaction(SalesDeliveryService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(SalesDeliveryPostingService.class.getDeclaredMethod("post", Long.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

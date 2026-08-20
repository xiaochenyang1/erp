package com.tuowei.erp.inventory.alert;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertDispositionMapper;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.service.InventoryAlertCommandService;
import com.tuowei.erp.inventory.alert.service.InventoryAlertQueryService;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
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
    void facadeKeepsQueriesAndCommandsBehindDedicatedCollaborators() {
        assertThat(constructorDependencies(InventoryAlertService.class))
                .containsExactlyInAnyOrder(
                        InventoryAlertQueryService.class,
                        InventoryAlertCommandService.class
                );
        assertThat(constructorDependencies(InventoryAlertQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryAlertRuleMapper.class,
                        InventoryAlertDispositionMapper.class,
                        AuditMetadataFactory.class,
                        WarehouseMapper.class,
                        ProductMapper.class,
                        InventoryBalanceMapper.class
                )
                .doesNotContain(InventoryAlertService.class, InventoryAlertCommandService.class);
        assertThat(constructorDependencies(InventoryAlertCommandService.class))
                .containsExactlyInAnyOrder(
                        InventoryAlertRuleMapper.class,
                        InventoryAlertDispositionMapper.class,
                        InventoryPostingService.class,
                        AuditMetadataFactory.class,
                        WarehouseMapper.class,
                        ProductMapper.class,
                        InventoryAlertQueryService.class
                )
                .doesNotContain(InventoryAlertService.class, InventoryBalanceMapper.class);
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
        assertReadOnly(InventoryAlertService.class.getDeclaredMethod(
                "listLowStock",
                Long.class,
                Long.class,
                AuditMetadata.class
        ));
        assertReadOnly(InventoryAlertQueryService.class.getDeclaredMethod(
                "listRules",
                Long.class,
                Long.class,
                Boolean.class
        ));
        assertReadOnly(InventoryAlertQueryService.class.getDeclaredMethod("listLowStock", Long.class, Long.class));
        assertReadOnly(InventoryAlertQueryService.class.getDeclaredMethod(
                "listLowStock",
                Long.class,
                Long.class,
                AuditMetadata.class
        ));
    }

    @Test
    void ruleAndDispositionWritesRemainRequiredTransactionsOnFacadeAndCommandService()
            throws NoSuchMethodException {
        Class<?>[] writeServices = {InventoryAlertService.class, InventoryAlertCommandService.class};
        for (Class<?> serviceType : writeServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "createRule",
                    com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "updateRule",
                    Long.class,
                    com.tuowei.erp.inventory.alert.web.InventoryAlertRuleUpdateRequest.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("enableRule", Long.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("disableRule", Long.class));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "handle",
                    Long.class,
                    Long.class,
                    String.class,
                    String.class
            ));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "reactivate",
                    Long.class,
                    Long.class
            ));
        }
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

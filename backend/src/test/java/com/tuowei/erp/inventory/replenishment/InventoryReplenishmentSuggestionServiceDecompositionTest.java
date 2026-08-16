package com.tuowei.erp.inventory.replenishment;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.alert.mapper.InventoryAlertRuleMapper;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.replenishment.mapper.InventoryReplenishmentSuggestionMapper;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionQueryService;
import com.tuowei.erp.inventory.replenishment.service.InventoryReplenishmentSuggestionService;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryReplenishmentSuggestionServiceDecompositionTest {

    @Test
    void facadeKeepsFilteringTenantGuardDisplayHydrationAndMappingBehindQueryService() {
        assertThat(constructorDependencies(InventoryReplenishmentSuggestionService.class))
                .contains(InventoryReplenishmentSuggestionQueryService.class)
                .doesNotContain(PurchaseOrderMapper.class);
        assertThat(constructorDependencies(InventoryReplenishmentSuggestionQueryService.class))
                .contains(PurchaseOrderMapper.class)
                .doesNotContain(InventoryReplenishmentSuggestionService.class);
    }

    @Test
    void facadeNormalizesNullQueryBeforeDelegatingList() {
        InventoryReplenishmentSuggestionQueryService queryService =
                mock(InventoryReplenishmentSuggestionQueryService.class);
        InventoryReplenishmentSuggestionService service = new InventoryReplenishmentSuggestionService(
                mock(InventoryReplenishmentSuggestionMapper.class),
                mock(InventoryAlertRuleMapper.class),
                mock(InventoryPostingService.class),
                mock(InventoryAlertService.class),
                mock(AuditMetadataFactory.class),
                mock(WarehouseMapper.class),
                mock(ProductMapper.class),
                mock(SupplierMapper.class),
                mock(PurchaseOrderService.class),
                queryService
        );

        service.list(null);

        verify(queryService).list(any(InventoryReplenishmentSuggestionPageQuery.class));
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(InventoryReplenishmentSuggestionService.class.getDeclaredMethod(
                "list",
                InventoryReplenishmentSuggestionPageQuery.class
        ));
        assertReadOnly(InventoryReplenishmentSuggestionQueryService.class.getDeclaredMethod(
                "list",
                InventoryReplenishmentSuggestionPageQuery.class
        ));
        assertReadOnly(InventoryReplenishmentSuggestionQueryService.class.getDeclaredMethod(
                "requireSuggestion",
                Long.class
        ));
    }

    @Test
    void suggestionWriteFlowKeepsRequiredTransactionsOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(InventoryReplenishmentSuggestionService.class.getDeclaredMethod(
                "create",
                com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest.class
        ));
        assertRequiredWriteTransaction(InventoryReplenishmentSuggestionService.class.getDeclaredMethod(
                "update",
                Long.class,
                com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest.class
        ));
        assertRequiredWriteTransaction(InventoryReplenishmentSuggestionService.class.getDeclaredMethod(
                "cancel",
                Long.class,
                com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest.class
        ));
        assertRequiredWriteTransaction(InventoryReplenishmentSuggestionService.class.getDeclaredMethod(
                "convertToPurchaseOrder",
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

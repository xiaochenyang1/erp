package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderServiceDecompositionTest {

    @Test
    void facadeKeepsReadSideSecurityBehindQueryService() {
        assertThat(constructorDependencies(SalesOrderService.class))
                .hasSize(12)
                .contains(SalesOrderQueryService.class)
                .contains(com.tuowei.erp.sales.order.service.SalesOrderPostingService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        InventoryPostingService.class
                );
        assertThat(constructorDependencies(SalesOrderQueryService.class))
                .contains(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(
                        com.tuowei.erp.sales.order.service.SalesOrderService.class,
                        ProductMapper.class,
                        ProductValidator.class,
                        WarehouseMapper.class,
                        InventoryPostingService.class,
                        WorkflowService.class
                );
        assertThat(constructorDependencies(com.tuowei.erp.sales.order.service.SalesOrderPostingService.class))
                .contains(
                        InventoryPostingService.class,
                        WorkflowService.class,
                        SalesOrderQueryService.class
                )
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        ProductValidator.class,
                        WarehouseMapper.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(SalesOrderService.class.getDeclaredMethod("list", SalesOrderPageQuery.class));
        assertReadOnly(SalesOrderService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(SalesOrderQueryService.class.getDeclaredMethod("list", SalesOrderPageQuery.class));
        assertReadOnly(SalesOrderQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(SalesOrderQueryService.class.getDeclaredMethod("assertCanView", SalesOrderEntity.class));
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

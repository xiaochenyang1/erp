package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.returnorder.service.SalesReturnNumberService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnPostingService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesReturnServiceDecompositionTest {

    @Test
    void salesReturnServiceKeepsReadSideSecurityBehindQueryService() {
        assertThat(constructorDependencies(SalesReturnService.class))
                .contains(SalesReturnQueryService.class, SalesReturnPostingService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        SalesOrderMapper.class,
                        SalesOrderLineMapper.class,
                        InventoryTransactionMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        FinancePostingService.class,
                        AccountPeriodGuard.class
                );
        assertThat(constructorDependencies(SalesReturnQueryService.class))
                .doesNotContain(SalesReturnService.class);
        assertThat(constructorDependencies(SalesReturnPostingService.class))
                .doesNotContain(
                        SalesReturnService.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        ProductValidator.class,
                        SalesReturnNumberService.class
                );
    }

    @Test
    void returnPostingKeepsRequiredWriteTransactionsOnFacadeAndCollaborator() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SalesReturnService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(SalesReturnPostingService.class.getDeclaredMethod("post", Long.class));
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

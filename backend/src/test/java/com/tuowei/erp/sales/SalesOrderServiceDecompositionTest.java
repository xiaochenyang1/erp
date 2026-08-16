package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.sales.order.service.SalesOrderQueryService;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderServiceDecompositionTest {

    @Test
    void salesOrderServiceKeepsReadSideSecurityBehindQueryService() {
        Set<Class<?>> constructorDependencies = constructorDependencies(SalesOrderService.class);

        assertThat(constructorDependencies)
                .contains(SalesOrderQueryService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class
                );
        assertThat(constructorDependencies(SalesOrderQueryService.class))
                .doesNotContain(SalesOrderService.class);
    }

    @Test
    void orderWriteKeepsRequiredWriteTransactionOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SalesOrderService.class.getDeclaredMethod("create", com.tuowei.erp.sales.order.web.SalesOrderCreateRequest.class));
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

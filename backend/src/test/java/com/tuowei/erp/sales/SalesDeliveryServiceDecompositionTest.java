package com.tuowei.erp.sales;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SalesDeliveryServiceDecompositionTest {

    @Test
    void salesDeliveryServiceKeepsReadSideSecurityBehindQueryService() {
        Set<Class<?>> constructorDependencies = Arrays.stream(SalesDeliveryService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());

        assertThat(constructorDependencies)
                .contains(SalesDeliveryQueryService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class
                );
    }
}

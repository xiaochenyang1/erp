package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnQueryService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReturnServiceDecompositionTest {

    @Test
    void facadeKeepsReadSideSecurityAndContextLookupsBehindQueryService() {
        assertThat(constructorDependencies(PurchaseReturnService.class))
                .contains(PurchaseReturnQueryService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        PurchaseOrderMapper.class,
                        WarehouseMapper.class
                );
        assertThat(constructorDependencies(PurchaseReturnQueryService.class))
                .contains(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        PurchaseOrderMapper.class,
                        WarehouseMapper.class
                )
                .doesNotContain(PurchaseReturnService.class, ProductMapper.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(PurchaseReturnService.class.getDeclaredMethod("list", PurchaseReturnPageQuery.class));
        assertReadOnly(PurchaseReturnService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReturnQueryService.class.getDeclaredMethod("list", PurchaseReturnPageQuery.class));
        assertReadOnly(PurchaseReturnQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReturnQueryService.class.getDeclaredMethod("assertCanView", PurchaseReturnEntity.class));
        assertReadOnly(PurchaseReturnQueryService.class.getDeclaredMethod("assertCanView", PurchaseReceiptEntity.class));
        assertReadOnly(PurchaseReturnQueryService.class.getDeclaredMethod(
                "assertCanView",
                com.tuowei.erp.purchase.order.model.PurchaseOrderEntity.class
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
}

package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptQueryService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReceiptServiceDecompositionTest {

    @Test
    void facadeKeepsReadSideSecurityBehindQueryService() {
        assertThat(constructorDependencies(PurchaseReceiptService.class))
                .hasSize(15)
                .contains(PurchaseReceiptQueryService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        ProductMapper.class,
                        PurchaseOrderMapper.class
                );
        assertThat(constructorDependencies(PurchaseReceiptQueryService.class))
                .contains(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(
                        PurchaseReceiptService.class,
                        ProductMapper.class,
                        PurchaseOrderMapper.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(PurchaseReceiptService.class.getDeclaredMethod("list", PurchaseReceiptPageQuery.class));
        assertReadOnly(PurchaseReceiptService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod("list", PurchaseReceiptPageQuery.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod(
                "assertCanView",
                PurchaseReceiptEntity.class
        ));
        assertReadOnly(PurchaseReceiptQueryService.class.getDeclaredMethod(
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

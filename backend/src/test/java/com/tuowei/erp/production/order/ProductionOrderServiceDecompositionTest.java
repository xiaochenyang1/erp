package com.tuowei.erp.production.order;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.service.ProductionOrderPostingService;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionOrderServiceDecompositionTest {

    @Test
    void facadeKeepsReadAndPostingOrchestrationBehindCollaborators() {
        assertThat(constructorDependencies(ProductionOrderService.class))
                .contains(ProductionOrderQueryService.class, ProductionOrderPostingService.class)
                .doesNotContain(
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class,
                        InventoryPostingService.class,
                        ProductionOperationService.class,
                        AttachmentService.class
                );
        assertThat(constructorDependencies(ProductionOrderQueryService.class))
                .contains(
                        ProductionOrderMapper.class,
                        ProductionOrderMaterialMapper.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(ProductionOrderService.class);
        assertThat(constructorDependencies(ProductionOrderPostingService.class))
                .contains(
                        ProductionOrderMapper.class,
                        ProductionOrderQueryService.class,
                        InventoryPostingService.class,
                        ProductionOperationService.class,
                        AttachmentService.class
                )
                .doesNotContain(ProductionOrderService.class, ProductionOrderMaterialMapper.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ProductionOrderService.class.getDeclaredMethod("list", ProductionOrderPageQuery.class));
        assertReadOnly(ProductionOrderService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(ProductionOrderQueryService.class.getDeclaredMethod("list", ProductionOrderPageQuery.class));
        assertReadOnly(ProductionOrderQueryService.class.getDeclaredMethod("getById", Long.class));
    }

    @Test
    void writeOrchestrationKeepsRequiredTransactionsOnFacadeAndCollaborator() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ProductionOrderService.class.getDeclaredMethod(
                "create",
                ProductionOrderCreateRequest.class
        ));
        assertRequiredWriteTransaction(ProductionOrderService.class.getDeclaredMethod("release", Long.class));
        assertRequiredWriteTransaction(ProductionOrderService.class.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(ProductionOrderPostingService.class.getDeclaredMethod("release", Long.class));
        assertRequiredWriteTransaction(ProductionOrderPostingService.class.getDeclaredMethod("cancel", Long.class));
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

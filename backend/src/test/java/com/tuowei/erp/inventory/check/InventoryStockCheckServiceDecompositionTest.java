package com.tuowei.erp.inventory.check;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckCommandService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckNumberService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckQueryService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckCreateRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckPageQuery;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

class InventoryStockCheckServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(InventoryStockCheckService.class))
                .containsExactlyInAnyOrder(InventoryStockCheckQueryService.class, InventoryStockCheckCommandService.class);
        assertThat(constructorDependencies(InventoryStockCheckQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryStockCheckMapper.class,
                        InventoryStockCheckLineMapper.class,
                        AuditMetadataFactory.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(InventoryStockCheckService.class, InventoryStockCheckCommandService.class);
        assertThat(constructorDependencies(InventoryStockCheckCommandService.class))
                .containsExactlyInAnyOrder(
                        InventoryStockCheckMapper.class,
                        InventoryStockCheckLineMapper.class,
                        InventoryStockCheckNumberService.class,
                        InventoryPostingService.class,
                        InventoryAdjustmentService.class,
                        AuditMetadataFactory.class,
                        WarehouseMapper.class,
                        ProductValidator.class,
                        AccountPeriodGuard.class,
                        AttachmentService.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        UserMapper.class
                )
                .doesNotContain(InventoryStockCheckService.class, InventoryStockCheckQueryService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        InventoryStockCheckQueryService queryService = mock(InventoryStockCheckQueryService.class);
        InventoryStockCheckCommandService commandService = mock(InventoryStockCheckCommandService.class);
        InventoryStockCheckService service = new InventoryStockCheckService(queryService, commandService);
        InventoryStockCheckCreateRequest createRequest = null;
        InventoryStockCheckUpdateRequest updateRequest = null;

        service.create(createRequest);
        service.getById(10L);
        service.list(null);
        service.postAdjustment(10L);
        service.update(10L, updateRequest);
        service.cancel(10L);

        verify(commandService).create(createRequest);
        verify(queryService).getById(10L);
        verify(queryService).list(any(InventoryStockCheckPageQuery.class));
        verify(commandService).postAdjustment(10L);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).cancel(10L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(InventoryStockCheckService.class.getDeclaredMethod("list", InventoryStockCheckPageQuery.class));
        assertReadOnly(InventoryStockCheckService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(InventoryStockCheckQueryService.class.getDeclaredMethod("list", InventoryStockCheckPageQuery.class));
        assertReadOnly(InventoryStockCheckQueryService.class.getDeclaredMethod("getById", Long.class));
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] writeServices = {InventoryStockCheckService.class, InventoryStockCheckCommandService.class};
        for (Class<?> type : writeServices) {
            assertRequired(type.getDeclaredMethod("create", InventoryStockCheckCreateRequest.class));
            assertRequired(type.getDeclaredMethod("postAdjustment", Long.class));
            assertRequired(type.getDeclaredMethod("update", Long.class, InventoryStockCheckUpdateRequest.class));
            assertRequired(type.getDeclaredMethod("cancel", Long.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequired(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

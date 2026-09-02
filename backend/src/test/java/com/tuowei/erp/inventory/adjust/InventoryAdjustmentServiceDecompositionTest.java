package com.tuowei.erp.inventory.adjust;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentCommandService;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentNumberService;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentQueryService;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentPageQuery;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
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

class InventoryAdjustmentServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(InventoryAdjustmentService.class))
                .containsExactlyInAnyOrder(InventoryAdjustmentQueryService.class, InventoryAdjustmentCommandService.class);
        assertThat(constructorDependencies(InventoryAdjustmentQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryAdjustmentMapper.class,
                        InventoryAdjustmentLineMapper.class,
                        AuditMetadataFactory.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(InventoryAdjustmentService.class, InventoryAdjustmentCommandService.class);
        assertThat(constructorDependencies(InventoryAdjustmentCommandService.class))
                .containsExactlyInAnyOrder(
                        InventoryAdjustmentMapper.class,
                        InventoryAdjustmentLineMapper.class,
                        InventoryAdjustmentNumberService.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        FinancePostingService.class,
                        AuditMetadataFactory.class,
                        WarehouseMapper.class,
                        ProductValidator.class,
                        AccountPeriodGuard.class,
                        AttachmentService.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        UserMapper.class
                )
                .doesNotContain(InventoryAdjustmentService.class, InventoryAdjustmentQueryService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        InventoryAdjustmentQueryService queryService = mock(InventoryAdjustmentQueryService.class);
        InventoryAdjustmentCommandService commandService = mock(InventoryAdjustmentCommandService.class);
        InventoryAdjustmentService service = new InventoryAdjustmentService(queryService, commandService);
        InventoryAdjustmentCreateRequest createRequest = null;

        service.create(createRequest);
        service.list(null);
        service.getById(10L);
        service.post(10L);
        service.cancel(10L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(InventoryAdjustmentPageQuery.class));
        verify(queryService).getById(10L);
        verify(commandService).post(10L);
        verify(commandService).cancel(10L);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionContracts() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{InventoryAdjustmentService.class, InventoryAdjustmentQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", InventoryAdjustmentPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
        for (Class<?> type : new Class<?>[]{InventoryAdjustmentService.class, InventoryAdjustmentCommandService.class}) {
            assertRequired(type.getDeclaredMethod("create", InventoryAdjustmentCreateRequest.class));
            assertRequired(type.getDeclaredMethod("post", Long.class));
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

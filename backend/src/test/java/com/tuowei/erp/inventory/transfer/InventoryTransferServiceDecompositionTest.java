package com.tuowei.erp.inventory.transfer;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferLineMapper;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferMapper;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferCommandService;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferNumberService;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferQueryService;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferService;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferPageQuery;
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

class InventoryTransferServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(InventoryTransferService.class))
                .containsExactlyInAnyOrder(InventoryTransferQueryService.class, InventoryTransferCommandService.class);
        assertThat(constructorDependencies(InventoryTransferQueryService.class))
                .containsExactlyInAnyOrder(
                        InventoryTransferMapper.class,
                        InventoryTransferLineMapper.class,
                        AuditMetadataFactory.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        ScopedUserResolver.class,
                        UserMapper.class
                )
                .doesNotContain(InventoryTransferService.class, InventoryTransferCommandService.class);
        assertThat(constructorDependencies(InventoryTransferCommandService.class))
                .containsExactlyInAnyOrder(
                        InventoryTransferMapper.class,
                        InventoryTransferLineMapper.class,
                        InventoryTransferNumberService.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        AuditMetadataFactory.class,
                        CurrentUserContext.class,
                        DataScopeService.class,
                        UserMapper.class,
                        WarehouseMapper.class,
                        ProductValidator.class,
                        AccountPeriodGuard.class,
                        AttachmentService.class
                )
                .doesNotContain(InventoryTransferService.class, InventoryTransferQueryService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        InventoryTransferQueryService queryService = mock(InventoryTransferQueryService.class);
        InventoryTransferCommandService commandService = mock(InventoryTransferCommandService.class);
        InventoryTransferService service = new InventoryTransferService(queryService, commandService);
        InventoryTransferCreateRequest createRequest = null;

        service.create(createRequest);
        service.list(null);
        service.getById(10L);
        service.post(10L);
        service.cancel(10L);

        verify(commandService).create(createRequest);
        verify(queryService).list(any(InventoryTransferPageQuery.class));
        verify(queryService).getById(10L);
        verify(commandService).post(10L);
        verify(commandService).cancel(10L);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionContracts() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{InventoryTransferService.class, InventoryTransferQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", InventoryTransferPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
        for (Class<?> type : new Class<?>[]{InventoryTransferService.class, InventoryTransferCommandService.class}) {
            assertRequired(type.getDeclaredMethod("create", InventoryTransferCreateRequest.class));
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

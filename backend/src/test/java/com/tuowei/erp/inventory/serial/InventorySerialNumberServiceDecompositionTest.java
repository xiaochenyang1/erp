package com.tuowei.erp.inventory.serial;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.serial.mapper.InventorySerialNumberMapper;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberCommandService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberQueryService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.serial.web.InventorySerialCreateRequest;
import com.tuowei.erp.inventory.serial.web.InventorySerialPageQuery;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
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

class InventorySerialNumberServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(autowiredConstructorDependencies(InventorySerialNumberService.class))
                .containsExactlyInAnyOrder(InventorySerialNumberQueryService.class, InventorySerialNumberCommandService.class);
        assertThat(constructorDependencies(InventorySerialNumberQueryService.class))
                .containsExactlyInAnyOrder(InventorySerialNumberMapper.class, ProductMapper.class, AuditMetadataFactory.class)
                .doesNotContain(InventorySerialNumberService.class, InventorySerialNumberCommandService.class);
        assertThat(constructorDependencies(InventorySerialNumberCommandService.class))
                .containsExactlyInAnyOrder(
                        InventorySerialNumberMapper.class, ProductMapper.class, AuditMetadataFactory.class,
                        InventorySerialNumberQueryService.class
                )
                .doesNotContain(InventorySerialNumberService.class);
    }

    @Test
    void facadeDelegatesAllApis() {
        InventorySerialNumberQueryService queryService = mock(InventorySerialNumberQueryService.class);
        InventorySerialNumberCommandService commandService = mock(InventorySerialNumberCommandService.class);
        InventorySerialNumberService service = new InventorySerialNumberService(queryService, commandService);
        InventorySerialCreateRequest request = null;

        service.create(request);
        service.list(null);
        service.issue(10L, "SO", "SO-1");
        service.scrap(10L);

        verify(commandService).create(request);
        verify(queryService).list(any(InventorySerialPageQuery.class));
        verify(commandService).issue(10L, "SO", "SO-1");
        verify(commandService).scrap(10L);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionContracts() throws NoSuchMethodException {
        assertReadOnly(InventorySerialNumberService.class.getDeclaredMethod("list", InventorySerialPageQuery.class));
        assertReadOnly(InventorySerialNumberQueryService.class.getDeclaredMethod("list", InventorySerialPageQuery.class));
        assertRequired(InventorySerialNumberService.class.getDeclaredMethod("create", InventorySerialCreateRequest.class));
        assertRequired(InventorySerialNumberCommandService.class.getDeclaredMethod("create", InventorySerialCreateRequest.class));
        assertRequired(InventorySerialNumberService.class.getDeclaredMethod("issue", Long.class, String.class, String.class));
        assertRequired(InventorySerialNumberCommandService.class.getDeclaredMethod("issue", Long.class, String.class, String.class));
        assertRequired(InventorySerialNumberService.class.getDeclaredMethod("scrap", Long.class));
        assertRequired(InventorySerialNumberCommandService.class.getDeclaredMethod("scrap", Long.class));
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

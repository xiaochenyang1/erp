package com.tuowei.erp.production.workcenter;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterCommandService;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterQueryService;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionWorkCenterServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(constructorDependencies(ProductionWorkCenterService.class))
                .containsExactlyInAnyOrder(ProductionWorkCenterQueryService.class, ProductionWorkCenterCommandService.class);
        assertThat(constructorDependencies(ProductionWorkCenterQueryService.class))
                .containsExactlyInAnyOrder(ProductionWorkCenterMapper.class, AuditMetadataFactory.class)
                .doesNotContain(ProductionWorkCenterService.class, ProductionWorkCenterCommandService.class);
        assertThat(constructorDependencies(ProductionWorkCenterCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionWorkCenterMapper.class,
                        ProductionRoutingMapper.class,
                        ProductionRoutingOperationMapper.class,
                        AuditMetadataFactory.class,
                        ProductionWorkCenterQueryService.class
                )
                .doesNotContain(ProductionWorkCenterService.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void facadeDelegatesEveryPublicApiToTheOwningCollaborator() {
        ProductionWorkCenterQueryService queryService = mock(ProductionWorkCenterQueryService.class);
        ProductionWorkCenterCommandService commandService = mock(ProductionWorkCenterCommandService.class);
        ProductionWorkCenterService service = new ProductionWorkCenterService(queryService, commandService);

        ProductionWorkCenterPageQuery query = new ProductionWorkCenterPageQuery();
        ProductionWorkCenterCreateRequest createRequest = mock(ProductionWorkCenterCreateRequest.class);
        ProductionWorkCenterUpdateRequest updateRequest = mock(ProductionWorkCenterUpdateRequest.class);
        PageResponse<ProductionWorkCenterResponse> pageResponse = mock(PageResponse.class);
        ProductionWorkCenterResponse response = mock(ProductionWorkCenterResponse.class);

        when(commandService.create(createRequest)).thenReturn(response);
        when(commandService.update(7L, updateRequest)).thenReturn(response);
        when(queryService.getById(7L)).thenReturn(response);
        when(queryService.list(query)).thenReturn(pageResponse);
        when(commandService.enable(7L)).thenReturn(response);
        when(commandService.disable(7L)).thenReturn(response);

        assertThat(service.create(createRequest)).isSameAs(response);
        assertThat(service.update(7L, updateRequest)).isSameAs(response);
        assertThat(service.getById(7L)).isSameAs(response);
        assertThat(service.list(query)).isSameAs(pageResponse);
        assertThat(service.enable(7L)).isSameAs(response);
        assertThat(service.disable(7L)).isSameAs(response);

        verify(commandService).create(createRequest);
        verify(commandService).update(7L, updateRequest);
        verify(queryService).getById(7L);
        verify(queryService).list(query);
        verify(commandService).enable(7L);
        verify(commandService).disable(7L);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyQueries() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ProductionWorkCenterService.class, ProductionWorkCenterQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
            assertReadOnly(type.getDeclaredMethod("list", ProductionWorkCenterPageQuery.class));
        }
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{ProductionWorkCenterService.class, ProductionWorkCenterCommandService.class}) {
            assertRequiredWriteTransaction(type.getDeclaredMethod("create", ProductionWorkCenterCreateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("update", Long.class, ProductionWorkCenterUpdateRequest.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWriteTransaction(type.getDeclaredMethod("disable", Long.class));
        }
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

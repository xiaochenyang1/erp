package com.tuowei.erp.production.routing;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.service.ProductionRoutingCommandService;
import com.tuowei.erp.production.routing.service.ProductionRoutingQueryService;
import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductionRoutingServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaboratorsWithoutReverseDependencies() {
        assertThat(constructorDependencies(ProductionRoutingService.class))
                .containsExactlyInAnyOrder(
                        ProductionRoutingQueryService.class,
                        ProductionRoutingCommandService.class
                );
        assertThat(constructorDependencies(ProductionRoutingQueryService.class))
                .containsExactlyInAnyOrder(
                        ProductionRoutingMapper.class,
                        ProductionRoutingOperationMapper.class,
                        ProductionBomMapper.class,
                        ProductionWorkCenterMapper.class,
                        AuditMetadataFactory.class
                )
                .doesNotContain(ProductionRoutingService.class, ProductionRoutingCommandService.class);
        assertThat(constructorDependencies(ProductionRoutingCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionRoutingMapper.class,
                        ProductionRoutingOperationMapper.class,
                        ProductionBomMapper.class,
                        ProductionWorkCenterMapper.class,
                        AuditMetadataFactory.class,
                        ProductionRoutingQueryService.class
                )
                .doesNotContain(ProductionRoutingService.class);
    }

    @Test
    void facadeDelegatesQueriesAndCommandsAndNormalizesNullListQuery() {
        ProductionRoutingQueryService queryService = mock(ProductionRoutingQueryService.class);
        ProductionRoutingCommandService commandService = mock(ProductionRoutingCommandService.class);
        ProductionRoutingService service = new ProductionRoutingService(queryService, commandService);

        service.list(null);
        service.getById(10L);
        service.create(new ProductionRoutingCreateRequest(
                "RT-10", "路线", 20L, null,
                List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 30L, null, null))
        ));
        service.update(10L, new ProductionRoutingUpdateRequest(
                "路线更新", null,
                List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 30L, null, null))
        ));
        service.enable(10L);
        service.disable(10L);

        verify(queryService).list(any(ProductionRoutingPageQuery.class));
        verify(queryService).getById(10L);
        verify(commandService).create(any(ProductionRoutingCreateRequest.class));
        verify(commandService).update(eq(10L), any(ProductionRoutingUpdateRequest.class));
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryCollaboratorKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ProductionRoutingService.class.getDeclaredMethod("list", ProductionRoutingPageQuery.class));
        assertReadOnly(ProductionRoutingService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(ProductionRoutingQueryService.class.getDeclaredMethod("list", ProductionRoutingPageQuery.class));
        assertReadOnly(ProductionRoutingQueryService.class.getDeclaredMethod("getById", Long.class));
    }

    @Test
    void facadeAndCommandCollaboratorKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] writeServices = {ProductionRoutingService.class, ProductionRoutingCommandService.class};
        for (Class<?> serviceType : writeServices) {
            assertRequiredWrite(serviceType.getDeclaredMethod("create", ProductionRoutingCreateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("update", Long.class, ProductionRoutingUpdateRequest.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(serviceType.getDeclaredMethod("disable", Long.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

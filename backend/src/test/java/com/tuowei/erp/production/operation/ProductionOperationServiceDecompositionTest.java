package com.tuowei.erp.production.operation;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.operation.service.ProductionOperationCommandService;
import com.tuowei.erp.production.operation.service.ProductionOperationQueryService;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.operation.web.ProductionOperationReportRequest;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionOperationServiceDecompositionTest {

    @Test
    void facadeDependsOnlyOnQueryAndCommandCollaborators() {
        assertThat(autowiredDependencies(ProductionOperationService.class))
                .containsExactlyInAnyOrder(ProductionOperationQueryService.class, ProductionOperationCommandService.class);
        assertThat(constructorDependencies(ProductionOperationQueryService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderOperationMapper.class, ProductionOrderMapper.class,
                        ProductionWorkCenterMapper.class, AuditMetadataFactory.class
                )
                .doesNotContain(ProductionOperationService.class, ProductionOperationCommandService.class);
        assertThat(constructorDependencies(ProductionOperationCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderOperationMapper.class, ProductionRoutingMapper.class,
                        ProductionRoutingOperationMapper.class, AuditMetadataFactory.class,
                        ProductionOperationQueryService.class
                )
                .doesNotContain(ProductionOperationService.class, ProductionOrderMapper.class,
                        ProductionWorkCenterMapper.class);
    }

    @Test
    void facadeAndCollaboratorsKeepTransactionSemantics() throws NoSuchMethodException {
        assertReadOnly(ProductionOperationService.class.getDeclaredMethod("listByOrder", Long.class));
        assertReadOnly(ProductionOperationQueryService.class.getDeclaredMethod("listByOrder", Long.class));
        assertReadOnly(ProductionOperationService.class.getDeclaredMethod(
                "assertReadyForCompletion", ProductionOrderEntity.class, BigDecimal.class));
        assertReadOnly(ProductionOperationQueryService.class.getDeclaredMethod(
                "assertReadyForCompletion", ProductionOrderEntity.class, BigDecimal.class));
        assertRequiredWrite(ProductionOperationService.class.getDeclaredMethod(
                "generateForReleasedOrder", ProductionOrderEntity.class, AuditMetadata.class));
        assertRequiredWrite(ProductionOperationCommandService.class.getDeclaredMethod(
                "generateForReleasedOrder", ProductionOrderEntity.class, AuditMetadata.class));
        assertRequiredWrite(ProductionOperationService.class.getDeclaredMethod(
                "report", Long.class, Long.class, ProductionOperationReportRequest.class));
        assertRequiredWrite(ProductionOperationCommandService.class.getDeclaredMethod(
                "report", Long.class, Long.class, ProductionOperationReportRequest.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> autowiredDependencies(Class<?> type) {
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

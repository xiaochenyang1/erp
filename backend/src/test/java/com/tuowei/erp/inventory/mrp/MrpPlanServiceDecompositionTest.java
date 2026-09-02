package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.service.MrpPlanQueryService;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCommandService;
import com.tuowei.erp.inventory.mrp.service.MrpPlanService;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.Test;
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

class MrpPlanServiceDecompositionTest {

    @Test
    void facadeKeepsPersistedRunQueriesBehindQueryServiceAndRetainsWriteOrchestration() {
        assertThat(constructorDependencies(MrpPlanService.class))
                .containsExactlyInAnyOrder(MrpPlanQueryService.class, MrpPlanCommandService.class);
        assertThat(constructorDependencies(MrpPlanQueryService.class))
                .contains(org.springframework.jdbc.core.JdbcTemplate.class, MrpRunMapper.class, MrpRunLineMapper.class)
                .doesNotContain(MrpPlanService.class, SequenceNumberGenerator.class);
        assertThat(constructorDependencies(MrpPlanCalculationService.class))
                .contains(org.springframework.jdbc.core.JdbcTemplate.class)
                .doesNotContain(MrpPlanService.class);
        assertThat(constructorDependencies(MrpPlanCommandService.class))
                .containsExactlyInAnyOrder(
                        com.tuowei.erp.common.security.AuditMetadataFactory.class,
                        MrpRunMapper.class,
                        MrpRunLineMapper.class,
                        SequenceNumberGenerator.class,
                        com.tuowei.erp.purchase.order.service.PurchaseOrderService.class,
                        com.tuowei.erp.production.order.service.ProductionOrderService.class,
                        com.tuowei.erp.masterdata.product.mapper.ProductMapper.class,
                        com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper.class,
                        com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper.class,
                        MrpPlanQueryService.class,
                        MrpPlanCalculationService.class
                )
                .doesNotContain(MrpPlanService.class);
    }

    @Test
    void facadeNormalizesNullQueryBeforeDelegatingList() {
        MrpPlanQueryService queryService = mock(MrpPlanQueryService.class);
        MrpPlanService service = new MrpPlanService(queryService, mock(MrpPlanCommandService.class));

        service.listRuns(null);

        verify(queryService).listRuns(any(MrpRunPageQuery.class));
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(MrpPlanService.class.getDeclaredMethod("listRuns", MrpRunPageQuery.class));
        assertReadOnly(MrpPlanService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(MrpPlanQueryService.class.getDeclaredMethod("listRuns", MrpRunPageQuery.class));
        assertReadOnly(MrpPlanQueryService.class.getDeclaredMethod("getById", Long.class));
        assertReadOnly(MrpPlanCalculationService.class.getDeclaredMethod(
                "calculate",
                com.tuowei.erp.common.security.AuditMetadata.class
        ));
        assertReadOnly(MrpPlanQueryService.class.getDeclaredMethod(
                "requireRun",
                Long.class,
                com.tuowei.erp.common.security.AuditMetadata.class
        ));
        assertReadOnly(MrpPlanQueryService.class.getDeclaredMethod(
                "requireLine",
                Long.class,
                Long.class,
                com.tuowei.erp.common.security.AuditMetadata.class
        ));
    }

    @Test
    void planningAndConversionRemainRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] commandServices = {MrpPlanService.class, MrpPlanCommandService.class};
        for (Class<?> serviceType : commandServices) {
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod("run"));
            assertRequiredWriteTransaction(serviceType.getDeclaredMethod(
                    "convertLine",
                    Long.class,
                    Long.class,
                    MrpConvertLineRequest.class
            ));
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

package com.tuowei.erp.qc;

import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.qc.inspection.service.QcInspectionCreateService;
import com.tuowei.erp.qc.inspection.service.QcInspectionNumberService;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.service.QcInspectionSourceAccess;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class QcInspectionServiceDecompositionTest {

    @Test
    void qcInspectionServiceKeepsCreationAndSourceReadsBehindDedicatedCollaborators() {
        Set<Class<?>> constructorDependencies = Arrays.stream(QcInspectionService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());

        assertThat(constructorDependencies)
                .contains(QcInspectionCreateService.class, QcInspectionSourceAccess.class)
                .doesNotContain(
                        SalesDeliveryMapper.class,
                        SalesDeliveryLineMapper.class,
                        ProductionOrderMapper.class,
                        QcInspectionNumberService.class
                );
    }

}

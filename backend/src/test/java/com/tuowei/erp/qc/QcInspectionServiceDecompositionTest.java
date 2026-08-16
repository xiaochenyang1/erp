package com.tuowei.erp.qc;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.service.QcInspectionCreateService;
import com.tuowei.erp.qc.inspection.service.QcInspectionNumberService;
import com.tuowei.erp.qc.inspection.service.QcInspectionQueryService;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.service.QcInspectionSourceAccess;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QcInspectionServiceDecompositionTest {

    @Test
    void qcInspectionServiceKeepsCreationQueryAndSourceReadsBehindDedicatedCollaborators() {
        Set<Class<?>> constructorDependencies = constructorDependencies(QcInspectionService.class);

        assertThat(constructorDependencies)
                .contains(
                        QcInspectionCreateService.class,
                        QcInspectionQueryService.class,
                        QcInspectionSourceAccess.class
                )
                .doesNotContain(
                        SalesDeliveryMapper.class,
                        SalesDeliveryLineMapper.class,
                        ProductionOrderMapper.class,
                        QcInspectionNumberService.class
                );
        assertThat(constructorDependencies(QcInspectionQueryService.class))
                .doesNotContain(QcInspectionService.class);
    }

    @Test
    void productionOrderReadsStayInNeutralSourceAccess() {
        assertThat(constructorDependencies(QcInspectionCreateService.class))
                .contains(QcInspectionSourceAccess.class)
                .doesNotContain(ProductionOrderMapper.class);
        assertThat(constructorDependencies(QcInspectionSourceAccess.class))
                .contains(ProductionOrderMapper.class);
    }

    @Test
    void facadeNormalizesNullQueriesBeforeDelegatingReadOperations() {
        QcInspectionQueryService queryService = mock(QcInspectionQueryService.class);
        QcInspectionService service = new QcInspectionService(
                mock(QcInspectionOrderMapper.class),
                mock(QcInspectionLineMapper.class),
                mock(PurchaseReceiptMapper.class),
                mock(PurchaseReceiptLineMapper.class),
                mock(AuditMetadataFactory.class),
                mock(QcInspectionCreateService.class),
                mock(QcInspectionSourceAccess.class),
                queryService
        );

        service.list(null);
        service.exportInspections(null);

        verify(queryService).list(any(QcInspectionPageQuery.class));
        verify(queryService).exportInspections(any(QcInspectionPageQuery.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}

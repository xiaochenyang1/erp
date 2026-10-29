package com.tuowei.erp.qc;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.service.QcInspectionCommandService;
import com.tuowei.erp.qc.inspection.service.QcInspectionCreateService;
import com.tuowei.erp.qc.inspection.service.QcInspectionNumberService;
import com.tuowei.erp.qc.inspection.service.QcInspectionQueryService;
import com.tuowei.erp.qc.inspection.service.QcInspectionService;
import com.tuowei.erp.qc.inspection.service.QcInspectionSourceAccess;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateRequest;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.system.attachment.service.AttachmentService;
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

class QcInspectionServiceDecompositionTest {

    @Test
    void qcInspectionServiceKeepsCreationQueriesAndCommandsBehindDedicatedCollaborators() {
        Set<Class<?>> constructorDependencies = constructorDependencies(QcInspectionService.class);

        assertThat(constructorDependencies)
                .containsExactlyInAnyOrder(
                        QcInspectionCreateService.class,
                        QcInspectionQueryService.class,
                        QcInspectionCommandService.class
                );
        assertThat(constructorDependencies(QcInspectionCommandService.class))
                .containsExactlyInAnyOrder(
                        QcInspectionOrderMapper.class,
                        QcInspectionLineMapper.class,
                        PurchaseReceiptMapper.class,
                        PurchaseReceiptLineMapper.class,
                        AuditMetadataFactory.class,
                        QcInspectionSourceAccess.class,
                        QcInspectionQueryService.class,
                        AttachmentService.class
                )
                .doesNotContain(QcInspectionService.class, QcInspectionCreateService.class);
        assertThat(constructorDependencies)
                .doesNotContain(
                        QcInspectionOrderMapper.class,
                        QcInspectionLineMapper.class,
                        PurchaseReceiptMapper.class,
                        PurchaseReceiptLineMapper.class,
                        QcInspectionSourceAccess.class,
                        SalesDeliveryMapper.class,
                        SalesDeliveryLineMapper.class,
                        ProductionOrderMapper.class,
                        QcInspectionNumberService.class,
                        AuditMetadataFactory.class,
                        AttachmentService.class
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
                mock(QcInspectionCreateService.class),
                queryService,
                mock(QcInspectionCommandService.class)
        );

        service.list(null);
        service.exportInspections(null);

        verify(queryService).list(any(QcInspectionPageQuery.class));
        verify(queryService).exportInspections(any(QcInspectionPageQuery.class));
    }

    @Test
    void facadeAndCommandServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        assertRequiredWriteTransaction(QcInspectionService.class.getDeclaredMethod(
                "update", Long.class, QcInspectionUpdateRequest.class));
        assertRequiredWriteTransaction(QcInspectionCommandService.class.getDeclaredMethod(
                "update", Long.class, QcInspectionUpdateRequest.class));
        assertRequiredWriteTransaction(QcInspectionService.class.getDeclaredMethod("submit", Long.class));
        assertRequiredWriteTransaction(QcInspectionCommandService.class.getDeclaredMethod("submit", Long.class));
        assertRequiredWriteTransaction(QcInspectionService.class.getDeclaredMethod(
                "judge", Long.class, QcInspectionJudgeRequest.class));
        assertRequiredWriteTransaction(QcInspectionCommandService.class.getDeclaredMethod(
                "judge", Long.class, QcInspectionJudgeRequest.class));
        assertRequiredWriteTransaction(QcInspectionService.class.getDeclaredMethod("cancel", Long.class));
        assertRequiredWriteTransaction(QcInspectionCommandService.class.getDeclaredMethod("cancel", Long.class));
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

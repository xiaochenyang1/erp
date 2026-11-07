package com.tuowei.erp.production.completion;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionMapper;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionReversalMapper;
import com.tuowei.erp.production.completion.service.ProductionCompletionCommandService;
import com.tuowei.erp.production.completion.service.ProductionCompletionReversalCommandService;
import com.tuowei.erp.production.completion.service.ProductionCompletionReversalService;
import com.tuowei.erp.production.completion.service.ProductionCompletionService;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionRequest;
import com.tuowei.erp.production.order.web.ProductionCompletionReversalRequest;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionCompletionServiceDecompositionTest {

    @Test
    void completionFacadeKeepsOrchestrationBehindCommandCollaborator() {
        assertThat(autowiredDependencies(ProductionCompletionService.class))
                .containsExactly(ProductionCompletionCommandService.class);
        assertThat(constructorDependencies(ProductionCompletionCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderService.class, ProductionOrderMapper.class, ProductionCompletionMapper.class,
                        InventoryPostingService.class, InventorySerialNumberService.class, AccountPeriodGuard.class,
                        FinancePostingService.class, AuditMetadataFactory.class, SequenceNumberGenerator.class,
                        ProductionOperationService.class, QcInspectionGate.class
                )
                .doesNotContain(ProductionCompletionService.class);
    }

    @Test
    void reversalFacadeKeepsOrchestrationBehindCommandCollaborator() {
        assertThat(autowiredDependencies(ProductionCompletionReversalService.class))
                .containsExactly(ProductionCompletionReversalCommandService.class);
        assertThat(constructorDependencies(ProductionCompletionReversalCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderService.class, ProductionOrderMapper.class,
                        ProductionCompletionReversalMapper.class, InventoryPostingService.class,
                        AccountPeriodGuard.class, FinancePostingService.class, AuditMetadataFactory.class,
                        SequenceNumberGenerator.class
                )
                .doesNotContain(ProductionCompletionReversalService.class);
    }

    @Test
    void facadeAndCommandsKeepRequiredWriteTransactions() throws NoSuchMethodException {
        assertRequiredWrite(ProductionCompletionService.class.getDeclaredMethod("complete", Long.class));
        assertRequiredWrite(ProductionCompletionService.class.getDeclaredMethod(
                "complete", Long.class, ProductionCompletionRequest.class));
        assertRequiredWrite(ProductionCompletionCommandService.class.getDeclaredMethod("complete", Long.class));
        assertRequiredWrite(ProductionCompletionCommandService.class.getDeclaredMethod(
                "complete", Long.class, ProductionCompletionRequest.class));
        assertRequiredWrite(ProductionCompletionReversalService.class.getDeclaredMethod(
                "reverseCompletion", Long.class, ProductionCompletionReversalRequest.class));
        assertRequiredWrite(ProductionCompletionReversalCommandService.class.getDeclaredMethod(
                "reverseCompletion", Long.class, ProductionCompletionReversalRequest.class));
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

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

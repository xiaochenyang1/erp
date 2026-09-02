package com.tuowei.erp.production.issue;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.issue.mapper.ProductionIssueLineMapper;
import com.tuowei.erp.production.issue.mapper.ProductionIssueMapper;
import com.tuowei.erp.production.issue.service.ProductionIssueCommandService;
import com.tuowei.erp.production.issue.service.ProductionIssueService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionIssueRequest;
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

class ProductionIssueServiceDecompositionTest {

    @Test
    void facadeKeepsProductionIssueOrchestrationBehindCommandCollaborator() {
        assertThat(autowiredDependencies(ProductionIssueService.class))
                .containsExactly(ProductionIssueCommandService.class);
        assertThat(constructorDependencies(ProductionIssueCommandService.class))
                .containsExactlyInAnyOrder(
                        ProductionOrderService.class,
                        ProductionOrderMapper.class,
                        ProductionOrderMaterialMapper.class,
                        ProductionIssueMapper.class,
                        ProductionIssueLineMapper.class,
                        InventoryPostingService.class,
                        InventorySerialNumberService.class,
                        AccountPeriodGuard.class,
                        FinancePostingService.class,
                        AuditMetadataFactory.class,
                        SequenceNumberGenerator.class
                )
                .doesNotContain(ProductionIssueService.class);
    }

    @Test
    void facadeDelegatesBothIssueEntrypoints() throws NoSuchMethodException {
        assertThat(ProductionIssueService.class.getDeclaredMethod("issue", Long.class))
                .isNotNull();
        assertThat(ProductionIssueService.class.getDeclaredMethod("issue", Long.class, ProductionIssueRequest.class))
                .isNotNull();
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ProductionIssueService.class.getDeclaredMethod("issue", Long.class));
        assertRequiredWriteTransaction(ProductionIssueService.class.getDeclaredMethod(
                "issue", Long.class, ProductionIssueRequest.class));
        assertRequiredWriteTransaction(ProductionIssueCommandService.class.getDeclaredMethod("issue", Long.class));
        assertRequiredWriteTransaction(ProductionIssueCommandService.class.getDeclaredMethod(
                "issue", Long.class, ProductionIssueRequest.class));
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

    private void assertRequiredWriteTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}

package com.tuowei.erp.issue.rule;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.service.ExceptionRuleScanService;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionRuleServiceDecompositionTest {

    @Test
    void facadeKeepsScanDependenciesBehindDedicatedService() {
        assertThat(constructorDependencies(ExceptionRuleService.class))
                .containsExactlyInAnyOrder(
                        AuditMetadataFactory.class,
                        ExceptionRuleMapper.class,
                        ExceptionRuleHitMapper.class,
                        ExceptionRuleScanService.class
                )
                .doesNotContain(
                        ExceptionTicketMapper.class,
                        ExceptionTicketService.class,
                        ExceptionSlaPolicyService.class,
                        InventoryAlertService.class,
                        ReceivableMapper.class,
                        PayableMapper.class,
                        OperationLogMapper.class,
                        Clock.class
                );
        assertThat(constructorDependencies(ExceptionRuleScanService.class))
                .containsExactlyInAnyOrder(
                        ExceptionRuleMapper.class,
                        ExceptionRuleHitMapper.class,
                        ExceptionTicketMapper.class,
                        ExceptionTicketService.class,
                        ExceptionSlaPolicyService.class,
                        InventoryAlertService.class,
                        ReceivableMapper.class,
                        PayableMapper.class,
                        OperationLogMapper.class,
                        Clock.class
                )
                .doesNotContain(ExceptionRuleService.class);
    }

    @Test
    void facadeAndScanServiceKeepRequiredWriteTransactions() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ExceptionRuleService.class.getDeclaredMethod("scanRule", Long.class));
        assertRequiredWriteTransaction(ExceptionRuleService.class.getDeclaredMethod("scanAll"));
        assertRequiredWriteTransaction(ExceptionRuleService.class.getDeclaredMethod("scanDueRules"));
        assertRequiredWriteTransaction(ExceptionRuleScanService.class.getDeclaredMethod(
                "scanRule",
                ExceptionRuleEntity.class,
                AuditMetadata.class
        ));
        assertRequiredWriteTransaction(ExceptionRuleScanService.class.getDeclaredMethod(
                "scanRules",
                List.class,
                AuditMetadata.class
        ));
        assertRequiredWriteTransaction(ExceptionRuleScanService.class.getDeclaredMethod("scanDueRules"));
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

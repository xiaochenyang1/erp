package com.tuowei.erp.workflow;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.service.WorkflowQueryService;
import com.tuowei.erp.workflow.service.WorkflowRecordCommandService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.service.WorkflowTaskTransitionService;
import com.tuowei.erp.workflow.web.WorkflowApprovalInfoResponse;
import com.tuowei.erp.workflow.web.WorkflowRecordPageQuery;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskPageQuery;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structure gate for the workflow E-1 query/posting split. Mirrors the sibling decomposition tests
 * (e.g. {@code PurchaseReceiptServiceDecompositionTest}): the facade must keep read-side mappers and
 * audit lookups behind {@link WorkflowQueryService}, the query service owns read-only transactions on
 * the read API, and the facade write API keeps {@code REQUIRED} write transactions.
 */
class WorkflowServiceDecompositionTest {

    @Test
    void facadeDelegatesReadsToQueryService() {
        // The workflow E-1 split extracts only the read side into WorkflowQueryService; the write
        // orchestration stays in the facade, so the facade legitimately keeps the three mappers for
        // writes. The invariant that must hold: reads are delegated to WorkflowQueryService, which
        // owns the mappers and audit factory and never reaches back into write-side collaborators.
        assertThat(constructorDependencies(WorkflowService.class))
                .hasSize(9)
                .contains(
                        WorkflowQueryService.class,
                        WorkflowTaskTransitionService.class,
                        WorkflowRecordCommandService.class
                )
                .doesNotContain(UserMapper.class, CurrentUserContext.class, SystemLogService.class);
        assertThat(constructorDependencies(WorkflowQueryService.class))
                .hasSize(4)
                .contains(
                        WorkflowInstanceMapper.class,
                        WorkflowTaskMapper.class,
                        WorkflowRecordMapper.class
                )
                .doesNotContain(
                        WorkflowService.class,
                        CurrentUserContext.class,
                        SystemLogService.class,
                        NotificationService.class,
                        WorkflowApprovalConfigService.class,
                        UserMapper.class
                );
        assertThat(constructorDependencies(WorkflowTaskTransitionService.class))
                .hasSize(8)
                .contains(
                        WorkflowInstanceMapper.class,
                        WorkflowTaskMapper.class,
                        WorkflowQueryService.class,
                        UserMapper.class,
                        WorkflowRecordCommandService.class
                )
                .doesNotContain(WorkflowService.class);
        assertThat(constructorDependencies(WorkflowRecordCommandService.class))
                .containsExactlyInAnyOrder(
                        WorkflowRecordMapper.class,
                        CurrentUserContext.class,
                        SystemLogService.class
                )
                .doesNotContain(WorkflowService.class, WorkflowTaskTransitionService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(WorkflowService.class.getDeclaredMethod("listTasks", WorkflowTaskPageQuery.class));
        assertReadOnly(WorkflowService.class.getDeclaredMethod("taskDetail", Long.class));
        assertReadOnly(WorkflowService.class.getDeclaredMethod("listRecords", WorkflowRecordPageQuery.class));
        assertReadOnly(WorkflowService.class.getDeclaredMethod("approvalInfo", String.class, Long.class));
        assertReadOnly(WorkflowQueryService.class.getDeclaredMethod("listTasks", WorkflowTaskPageQuery.class));
        assertReadOnly(WorkflowQueryService.class.getDeclaredMethod("taskDetail", Long.class));
        assertReadOnly(WorkflowQueryService.class.getDeclaredMethod("listRecords", WorkflowRecordPageQuery.class));
        assertReadOnly(WorkflowQueryService.class.getDeclaredMethod("approvalInfo", String.class, Long.class));
        assertReadOnly(WorkflowQueryService.class.getDeclaredMethod("requireScopedTask", Long.class));
    }

    @Test
    void facadeKeepsRequiredWriteTransactionsOnWriteApi() throws NoSuchMethodException {
        assertRequiredWriteTransaction(WorkflowService.class.getDeclaredMethod(
                "submit", String.class, Long.class, String.class, String.class, String.class));
        assertRequiredWriteTransaction(WorkflowService.class.getDeclaredMethod(
                "transfer", Long.class, Long.class, String.class));
        assertRequiredWriteTransaction(WorkflowService.class.getDeclaredMethod(
                "escalate", Long.class, Long.class, String.class));
        assertRequiredWriteTransaction(WorkflowService.class.getDeclaredMethod(
                "withdraw", String.class, Long.class, String.class));
        assertRequiredWriteTransaction(WorkflowTaskTransitionService.class.getDeclaredMethod(
                "transfer", Long.class, Long.class, String.class));
        assertRequiredWriteTransaction(WorkflowTaskTransitionService.class.getDeclaredMethod(
                "escalate", Long.class, Long.class, String.class));
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

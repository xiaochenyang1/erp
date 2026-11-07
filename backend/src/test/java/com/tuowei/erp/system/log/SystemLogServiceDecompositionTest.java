package com.tuowei.erp.system.log;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.log.mapper.AuditLogMapper;
import com.tuowei.erp.system.log.mapper.LoginLogMapper;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.service.SystemLogQueryService;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.LoginLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SystemLogServiceDecompositionTest {

    @Test
    void facadeKeepsWritesAndDelegatesReadSideToQueryService() {
        assertThat(constructorDependencies(SystemLogService.class))
                .hasSize(8)
                .contains(
                        LoginLogMapper.class,
                        OperationLogMapper.class,
                        AuditLogMapper.class,
                        CurrentUserContext.class,
                        UserMapper.class,
                        ClientIpResolver.class,
                        Clock.class,
                        SystemLogQueryService.class
                );
        assertThat(constructorDependencies(SystemLogQueryService.class))
                .containsExactlyInAnyOrder(
                        LoginLogMapper.class,
                        OperationLogMapper.class,
                        AuditLogMapper.class,
                        CurrentUserContext.class
                )
                .doesNotContain(
                        SystemLogService.class,
                        UserMapper.class,
                        ClientIpResolver.class,
                        Clock.class
                );
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(SystemLogService.class.getDeclaredMethod(
                "listLoginLogs", LoginLogPageQuery.class));
        assertReadOnly(SystemLogService.class.getDeclaredMethod(
                "listOperationLogs", OperationLogPageQuery.class));
        assertReadOnly(SystemLogService.class.getDeclaredMethod(
                "getOperationLog", Long.class));
        assertReadOnly(SystemLogService.class.getDeclaredMethod(
                "listAuditLogs", AuditLogPageQuery.class));

        assertReadOnly(SystemLogQueryService.class.getDeclaredMethod(
                "listLoginLogs", LoginLogPageQuery.class));
        assertReadOnly(SystemLogQueryService.class.getDeclaredMethod(
                "listOperationLogs", OperationLogPageQuery.class));
        assertReadOnly(SystemLogQueryService.class.getDeclaredMethod(
                "getOperationLog", Long.class));
        assertReadOnly(SystemLogQueryService.class.getDeclaredMethod(
                "listAuditLogs", AuditLogPageQuery.class));
    }

    @Test
    void facadeKeepsRequiredTransactionsOnLogWrites() throws NoSuchMethodException {
        assertRequiredWriteTransaction(SystemLogService.class.getDeclaredMethod(
                "recordLoginSuccess",
                Long.class,
                String.class,
                String.class,
                HttpServletRequest.class
        ));
        assertRequiredWriteTransaction(SystemLogService.class.getDeclaredMethod(
                "recordLoginFailure",
                String.class,
                String.class,
                HttpServletRequest.class
        ));
        assertRequiredWriteTransaction(SystemLogService.class.getDeclaredMethod(
                "recordOperation",
                ErpPrincipal.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                HttpServletRequest.class
        ));
        assertRequiredWriteTransaction(SystemLogService.class.getDeclaredMethod(
                "recordAudit",
                String.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class
        ));
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

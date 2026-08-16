package com.tuowei.erp.finance.voucher;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.voucher.model.ManualVoucherEntity;
import com.tuowei.erp.finance.voucher.service.ManualVoucherQueryService;
import com.tuowei.erp.finance.voucher.service.ManualVoucherService;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ManualVoucherServiceDecompositionTest {

    @Test
    void facadeKeepsReadFilteringTenantGuardAndLineMappingBehindQueryService() {
        assertThat(constructorDependencies(ManualVoucherService.class))
                .contains(ManualVoucherQueryService.class);
        assertThat(constructorDependencies(ManualVoucherQueryService.class))
                .doesNotContain(ManualVoucherService.class);
    }

    @Test
    void facadeAndQueryServiceKeepReadOnlyTransactions() throws NoSuchMethodException {
        assertReadOnly(ManualVoucherService.class.getDeclaredMethod("list", ManualVoucherPageQuery.class));
        assertReadOnly(ManualVoucherService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod("list", ManualVoucherPageQuery.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod("detail", Long.class));
        assertReadOnly(ManualVoucherQueryService.class.getDeclaredMethod(
                "requireVoucher",
                Long.class,
                AuditMetadata.class
        ));
    }

    @Test
    void postingStateMachineKeepsRequiredWriteTransactionsOnFacade() throws NoSuchMethodException {
        assertRequiredWriteTransaction(ManualVoucherService.class.getDeclaredMethod(
                "create",
                com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest.class
        ));
        assertRequiredWriteTransaction(ManualVoucherService.class.getDeclaredMethod("post", Long.class));
        assertRequiredWriteTransaction(ManualVoucherService.class.getDeclaredMethod(
                "cancel",
                Long.class,
                String.class
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
